/**
 * Copyright (c) 2011-2012 Armin Töpfer
 *
 * This file is part of QuasiRecomb.
 *
 * QuasiRecomb is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or any later version.
 *
 * QuasiRecomb is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * QuasiRecomb. If not, see <http://www.gnu.org/licenses/>.
 */
package ch.ethz.bsse.quasirecomb.model.hmm;

import ch.ethz.bsse.quasirecomb.informatioholder.OptimalResult;
import ch.ethz.bsse.quasirecomb.model.Globals;
import ch.ethz.bsse.quasirecomb.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Armin Töpfer (armin.toepfer [at] gmail.com)
 */
public class SingleEM {

    private long time = -1;
    private StringBuilder sb = new StringBuilder();
    private JHMM jhmm;
    private int iterations = 0;
    private int N;
    private int K;
    private int L;
    private int n;
    private Map<byte[], Integer> reads;
    private byte[][] haplotypesArray;
    private double llh_opt;
    private OptimalResult or;
    private double delta;

    public SingleEM(int N, int K, int L, int n, Map<byte[], Integer> reads, byte[][] haplotypesArray, double delta) {
        this.N = N;
        this.K = K;
        this.L = L;
        this.n = n;
        this.reads = reads;
        this.haplotypesArray = haplotypesArray;
        this.delta = delta;
        start(null);
    }

    public SingleEM(OptimalResult or) {
        this.N = or.getN();
        this.K = or.getK();
        this.L = or.getL();
        this.n = or.getn();
        this.reads = or.getReads();
        this.haplotypesArray = or.getHaplotypesArray();
        this.delta = Globals.DELTA_LLH_HARDER;
        start(or);
    }

    public SingleEM(int N, int K, int L, int n, Map<byte[], Integer> reads, byte[][] haplotypesArray, double delta, OptimalResult or) {
        this.N = N;
        this.K = K;
        this.L = L;
        this.n = n;
        this.reads = reads;
        this.haplotypesArray = haplotypesArray;
        this.delta = delta;
        start(or);
    }

    private void start(OptimalResult givenPrior) {
        this.llh_opt = Globals.getMAX_LLH();
        time(false);
        if (givenPrior == null) {
            jhmm = new JHMM(reads, N, L, K, n, Globals.ESTIMATION_EPSILON);
        } else {
            jhmm = new JHMM(givenPrior);
        }

        double llh = Double.MIN_VALUE;
        double oldllh = Double.MIN_VALUE;
        List<Double> history = new ArrayList<>();
        boolean broken = false;
        do {
            if (iterations % 10 == 0 && iterations > 0) {
                Globals.maxMAX_LLH(llh);
                this.llh_opt = Math.max(Globals.getMAX_LLH(), this.llh_opt);
            }
            history.add(llh);
            oldllh = llh;
            llh = jhmm.getLoglikelihood();
            if (((oldllh - llh) / llh) < 1e-5 || Globals.NO_BREAK_THRESHOLD) {
                if (iterations % Globals.MAX_PRE_BREAK == 0 && iterations > 0) {
                    Globals.log("BIAS CHECK! This: " + llh + "\tbias: " + (llh - (this.llh_opt * Globals.BIAS)) + "\topt:" + this.llh_opt);
                    if ((llh - (this.llh_opt * Globals.BIAS)) < this.llh_opt) {
                        Globals.log("pre break;\t");
                        broken = true;
                        break;
                    }
                }
            }

            if (iterations > 500) {
                if (history.get(iterations - 500) - llh > -1) {
                    Globals.log("break 500;\t");
                    broken = true;
                    break;
                }
            }
            log(llh);

            if (Globals.DEBUG) {
                if ((oldllh - llh) / llh == -1) {
                    Globals.log("0\t");
                } else {
                    Globals.log((oldllh - llh) / llh + "\t" + Math.abs((oldllh - llh) / llh) + "\t" + this.delta + "\t");
                }
                Globals.log(llh + "\n");
            }
            if (Double.isNaN(llh)) {
                System.out.println("");
                
                for (ReadHMM r : jhmm.getReadHMMMap().keySet()) {
                    r.checkConsistency();
                }
                
                for (int k = 0; k < K; k++) {
                    if (Double.isNaN(jhmm.getPi()[k])) {
                        System.out.println("");
                    }
                }
                for (int j = 0; j < L; j++) {
                    for (int k = 0; k < K; k++) {
                        for (int v = 0; v < n; v++) {
                            if (Double.isNaN(jhmm.getMu()[j][k][v])) {
                                System.out.println("");
                            }
                        }
                    }
                }
                for (int j = 0; j < L - 1; j++) {
                    for (int k = 0; k < K; k++) {
                        for (int l = 0; l < K; l++) {
                            if (Double.isNaN(jhmm.getRho()[j][k][l])) {
                                System.out.println("");
                            }
                        }
                    }
                }
                System.out.println("");
            }
            jhmm.restart();
            iterations++;

        } while (Math.abs((oldllh - llh) / llh) > this.delta);
        if (!broken) {
            Globals.log("\t\t");
        }

        Globals.log((String.valueOf((oldllh - llh) / llh).contains("-") ? "dist: 1e-" + (String.valueOf((oldllh - llh) / llh).split("-")[1]) : String.valueOf((oldllh - llh) / llh)) + "(" + iterations + ")" + this.llh_opt + "\tthis: " + llh + "\topt:" + this.llh_opt + "\tmax:" + Globals.getMAX_LLH());

        this.calcBic(llh);

        if (Globals.DEBUG) {
            Globals.log("####");
        }
        Globals.printPercentage(K);
    }

    public void calcBic(double llh) {
        //overview
        double BIC_current = 0;

        // calculate loglikelihood from scaling factors
        for (ReadHMM r : jhmm.getReadHMMMap().keySet()) {
            int times = jhmm.getReadHMMMap().get(r);
            for (int j = 0; j < jhmm.getL(); j++) {
                BIC_current += Math.log(r.getC(j)) * times;
            }
        }

        // count free parameters
        int freeParameters = 0;
        double ERROR = 1e-8;

        double[][][] rho = jhmm.getRho();
        double[][][] mu = jhmm.getMu();
        double[] pi = jhmm.getPi();
        for (int j = 0; j < rho.length; j++) {
            for (int k = 0; k < rho[j].length; k++) {
                for (int l = 0; l < rho[j][k].length; l++) {
                    if (rho[j][k][l] > ERROR) {
                        freeParameters++;
                    }
                }
            }
        }
        for (int i = 0; i < mu.length; i++) {
            for (int j = 0; j < mu[i].length; j++) {
                for (int k = 0; k < mu[i][j].length; k++) {
                    if (mu[i][j][k] > ERROR) {
                        freeParameters++;
                    }
                }
            }
        }
        for (int k = 0; k < pi.length; k++) {
            if (pi[k] > ERROR) {
                freeParameters++;
            }
        }

        BIC_current -= (freeParameters / 2d) * Math.log(N);

        if (Globals.LOG_BIC) Utils.appendFile(Globals.savePath + "BIC-" + K + ".txt", BIC_current + "\t" + freeParameters + "\n");

        double[][][] mu_tmp = new double[L][K][n];
        for (int j = 0; j < L; j++) {
            for (int k = 0; k < K; k++) {
                System.arraycopy(jhmm.getMu()[j][k], 0, mu_tmp[j][k], 0, n);
            }
        }
        this.or = new OptimalResult(N, K, L, n, reads, haplotypesArray,
                Arrays.copyOf(jhmm.getRho(), jhmm.getRho().length),
                Arrays.copyOf(jhmm.getPi(),
                jhmm.getPi().length),
                mu_tmp,
                llh,
                BIC_current, jhmm.getPrior_rho(), jhmm.getEps());
        if (llh >= llh_opt) {
            Globals.maxMAX_LLH(llh);
        }
    }

    private long time(boolean show) {
        long t = 0;
        if (time == -1) {
            time = System.currentTimeMillis();
        } else {
            t = (System.currentTimeMillis() - time);
            if (show) {
                sb.append(t).append("\t\t");
                if (Globals.DEBUG) {
                    Globals.log(iterations + "\t" + t + "\t\t");
                }
            }
            time = System.currentTimeMillis();
        }
        return t;
    }

    private void log(double llh) {
        Globals.runtime.add((int) time(true));
        sb.append(llh).append("\t\t").append("\n");
    }

    public OptimalResult getOptimalResult() {
        return or;
    }

    public double getLlh_opt() {
        return llh_opt;
    }
}
