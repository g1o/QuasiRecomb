/**
 * Copyright (c) 2011-2013 Armin Töpfer
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
package ch.ethz.bsse.quasirecomb.simulation;

import ch.ethz.bsse.quasirecomb.informationholder.Read;
import ch.ethz.bsse.quasirecomb.utils.BitMagic;
import ch.ethz.bsse.quasirecomb.utils.Frequency;
import ch.ethz.bsse.quasirecomb.utils.Utils;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.javatuples.Pair;

/**
 * @author Armin Töpfer (armin.toepfer [at] gmail.com)
 */
public class CallableSimulator implements Callable<Pair<Read, Read>> {

    private int length;
    private double epsilon;
    private int n;
    private String[] haplotypes;
    private int hap;
    private int start;
    private int start2;

    public CallableSimulator(int length, double epsilon, int n, String[] haplotypes, int hap, int start, int start2) {
        this.length = length;
        this.epsilon = epsilon;
        this.n = n;
        this.haplotypes = haplotypes;
        this.hap = hap;
        this.start = start;
        this.start2 = start2;
    }

    @Override
    public Pair<Read, Read> call() throws Exception {
        char[] readArray = new char[length];
        for (int j = 0; j < length; j++) {
            //error
            if (epsilon > 0d) {
                Map<Character, Double> baseMap = new ConcurrentHashMap<>();
                for (int v = 0; v < n; v++) {
                    char x = Utils.reverseChar(v);
                    if (haplotypes[hap].charAt(j + start) == x) {
                        baseMap.put(x, 1.0 - (n - 1.0) * epsilon);
                    } else {
                        baseMap.put(x, epsilon);
                    }
                }
                Frequency<Character> errorF = new Frequency<>(baseMap);
                readArray[j] = errorF.roll();
            } else {
                readArray[j] = haplotypes[hap].charAt(j + start);
            }
        }
        StringBuilder sb = new StringBuilder(length);
        for (int j = 0; j < length; j++) {
            sb.append(readArray[j]);
        }
        String read = sb.toString().replace("-", "");
        boolean[] cigar = new boolean[sb.length()];
        for (int i = 0; i < sb.length(); i++) {
            cigar[i] = true;
        }
        Read r1 = new Read(BitMagic.splitReadIntoBytes(sb.toString()), start, start + read.length(), cigar);

        readArray = new char[length];
        for (int j = 0; j < length; j++) {
            final int pos = j + start2;
            //error
            if (epsilon > 0d) {
                Map<Character, Double> baseMap = new ConcurrentHashMap<>();
                for (int v = 0; v < n; v++) {
                    char x = Utils.reverseChar(v);
                    if (haplotypes[hap].charAt(pos) == x) {
                        baseMap.put(x, 1.0 - (n - 1.0) * epsilon);
                    } else {
                        baseMap.put(x, epsilon);
                    }
                }
                Frequency<Character> errorF = new Frequency<>(baseMap);
                readArray[j] = errorF.roll();
            } else {
                readArray[j] = haplotypes[hap].charAt(pos);
            }
        }
        StringBuilder sb2 = new StringBuilder(length);
        for (int j = 0; j < length; j++) {
            sb2.append(readArray[j]);
        }
        read = sb2.toString().replace("-", "");
        cigar = new boolean[sb2.length()];
        for (int i = 0; i < sb2.length(); i++) {
            cigar[i] = true;
        }
        Read r2 = new Read(BitMagic.splitReadIntoBytes(read), start2, start2 + read.length(),cigar);
        return Pair.with(r1, r2);
    }
}
