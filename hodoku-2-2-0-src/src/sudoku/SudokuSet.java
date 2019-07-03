/*
 * Copyright (C) 2008-12  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */

package sudoku;

/**
 * Hilfsklasse für die Fischsuche:  鱼类搜索的辅助类
 * SudokuSet是一个大小为81的整数数组，可以保存0到81之间的值，数组中的值按顺序插入
 * Ein SudokuSet ist ein Integer-Array der Größe 81, das Werte zwischen 0 und 81
 * aufnehmen kann. Die Werte innerhalb des Arrays werden sortiert eingefügt.
 * 出于性能原因，值在位掩码中重复。像merge（）或contains（）可以更快地执行
 * Aus Performancegründen werden die Werte in einer Bitmask dupliziert. Operationen
 * wie merge() oder contains() können damit wesentlich schneller ausgeführt werden.
 * 在位图中，值“0”被映射为“0x00000001”。
 * 最大的代表性，每个int的值是“0x80000000”并且代表“31”（值“0”被映射为“0x00000001”，1右移31位），
 * 因此3 int导致值0到95 （3个int有96位）
 * In der Bitmap wird der Wert "0" als "0x00000001" abgebildet. Der größte darstellbare
 * Wert pro int ist "0x80000000" und steht für "31", 3 int ergeben daher die Werte 0 - 95.
 * int mask1:  0 - 31
 * int mask2: 32 - 63
 * int mask3: 64 - 95
 *
 * 可以比较SudokuSet的多个实例。这很特别
 * 可以检查一个sudokuSet的值是否包含在另一个中。还可以形成SudokuSets的有效关联（对应于混合）。
 * Mehrere Instanzen von SudokuSet können miteinander verglichen werden. Speziell ist es
 * möglich zu prüfen, ob Werte eines SudokuSet in einem anderen enthalten sind. Außerdem
 * können effizient Vereinigungen von SudokuSets gebildet werden (entspricht mischen).
 *
 * @author hobiwan
 */
public class SudokuSet extends SudokuSetBase implements Cloneable {
    // für jede der 256 möglichen Kombinationen von Bits das entsprechende Array
    //对于256个可能的位组合中的每一个，相应的数组
    private static int[][] possibleValues = new int[256][8];
    // und zu jeder Zahl die Länge des Arrays
    //对于每个数字，数组的长度
    public static int[] anzValues = new int[256];
    private static final long serialVersionUID = 1L;
    //每个网格的
    private int[] values = null;
    private int anz = 0;
    
    static {
        // possibleValues initialisieren， 用于拆分mask掩码，还原buddies索引
        for (int i = 0; i < 256; i++) {   //i 255   11111111    0到255的掩码 分别映射的 数字组合（0-7）
            int index = 0;
            int mask = 1;
            for (int j = 0; j < 8; j++) { // j: 0-7     0-1；1-10；2-100；3-1000；4-10000；5-100000；6-1000000；7-10000000  
                if ((i & mask) != 0) {
                    possibleValues[i][index++] = j;   //掩码对应的数组
                }
                mask <<= 1;
            }
            anzValues[i] = index;         //掩码对应的数字个数
        }
    }
    
    /** Creates a new instance of SudokuSet */
    public SudokuSet() {
    }
    
    public SudokuSet(SudokuSetBase init) {
        super(init);
    }
    
    public SudokuSet(boolean full) {
        super(full);
    }
    
    @Override
    public SudokuSet clone() {
        SudokuSet newSet = null;
        newSet = (SudokuSet) super.clone();
        // dont clone the array (for performance reasons - might not be necessary)
        values = null;
        initialized = false;
//        if (values != null) {
//            newSet.values = Arrays.copyOf(values, values.length);
//        }
        return newSet;
    }
   
    public int get(int index) {
        if (! isInitialized()) {
            initialize();
        }
        return values[index];
    }
    
    public int size() {
        if (isEmpty()) {
            return 0;
        }
        if (! isInitialized()) {
            initialize();
        }
        return anz;
    }
    
    @Override
    public void clear() {
        super.clear();
        anz = 0;
    }
    
    public int[] getValues() {
        if (! initialized) {
            initialize();
        }
        return values;
    }
    
    /** 
     * 检查值s1中是否出现值中的所有元素。
     * 所有未密封的候选数都被写入SudokuSet鳍
     * pr�ft, ob alle Elemente in values im Set s1 vorkommen.
     * Alle nicht enthaltenen Kandidaten werden in das SudokuSet fins geschrieben
     * @param s1
     * @param fins
     * @return  
     */
    public boolean isCovered(SudokuSet s1, SudokuSet fins) {
        long m1 = ~s1.mask1 & mask1;
        long m2 = ~s1.mask2 & mask2;
        boolean covered = true;
        if (m1 != 0) {
            covered = false;
            fins.mask1 = m1;
            fins.initialized = false;
        }
        if (m2 != 0) {
            covered = false;
            fins.mask2 = m2;
            fins.initialized = false;
        }
        return covered;
    }
    
    // 将 mask掩码组合  分解成索引  ，分段还原
    private void initialize() {
        if (values == null) {
            values = new int[81];
        }
        int index = 0;
        if (mask1 != 0) {
            for (int i = 0; i < 64; i += 8) {  //0-7,8-15,16-23,24-31,32-39,40-47,48-55,56-63，每次检查8个
                int mIndex = (int)((mask1 >> i) & 0xFF);    //& 0xFF   11111111  （0-7,8-15,16-23,24-31,32-39,40-47,48-55,56-63）
                for (int j = 0; j < anzValues[mIndex]; j++) {  //此掩码对应的数字个数
                    values[index++] = possibleValues[mIndex][j] + i;       //index是数组索引； 值表示buddies的索引； possibleValues：掩码对应的数组
                }
            }
        }
        if (mask2 != 0) {
            for (int i = 0; i < 24; i += 8) {
                int mIndex = (int)((mask2 >> i) & 0xFF);
                for (int j = 0; j < anzValues[mIndex]; j++) {
                    values[index++] = possibleValues[mIndex][j] + i + 64;
                }
            }
        }
        setInitialized(true);
        setAnz(index);
    }
    
    @Override
    public String toString() {
        if (! isInitialized()) {
            initialize();
        }
        if (anz == 0) {
            return "empty!";
        }
        StringBuilder tmp = new StringBuilder();
        tmp.append(Integer.toString(values[0]));
        for (int i = 1; i < anz; i++) {
            tmp.append(" ").append(Integer.toString(values[i]));
        }
        tmp.append(" ").append(pM(mask1)).append("/").append(pM(mask2));
        return tmp.toString();
    }
    
    public static void main(String[] args) {
        SudokuSet a = new SudokuSet();
        a.add(5);
        a.add(1);
        a.add(7);
        a.add(3);
        a.add(0);
        System.out.println("a: " + a);
        SudokuSet b = new SudokuSet();
        b.add(2);
        b.add(4);
        b.add(3);
        System.out.println("b: " + b);
        System.out.println(a.intersects(b));
        SudokuSet c = new SudokuSet();
        c.add(0);
        c.add(1);
        c.add(5);
        c.add(7);
        c.add(10);
        System.out.println("c: " + c);
        SudokuSet fins = new SudokuSet();
        //System.out.println(a.isCovered(b, c, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, fins));
//        System.out.println(a.isCovered(c, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, fins));
        System.out.println("fins: " + fins);
        a.remove(5);
        System.out.println("a: " + a);
        a.remove(0);
        System.out.println("a: " + a);
        a.remove(7);
        System.out.println("a: " + a);
        a.remove(3);
        System.out.println("a: " + a);
        a.remove(12);
        System.out.println("a: " + a);
        a.remove(1);
        System.out.println("a: " + a);
        a.remove(12);
        System.out.println("a: " + a);
        a.add(70);
        a.add(10);
        a.add(80);
        System.out.println("a: " + a);
        a.clear();
        a.add(0);
        System.out.println("a: " + a);
    }

    public void setValues(int[] values) {
        this.values = values;
    }

    public int getAnz() {
        return anz;
    }

    public void setAnz(int anz) {
        this.anz = anz;
    }
}
