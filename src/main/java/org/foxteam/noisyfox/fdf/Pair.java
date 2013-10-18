package org.foxteam.noisyfox.fdf;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 上午12:44
 * 任意类型的对
 */
public final class Pair<T1, T2> {
    private T1 value1;
    private T2 value2;

    public Pair() {
        this(null, null);
    }

    public Pair(T1 v1, T2 v2) {
        value1 = v1;
        value2 = v2;
    }

    public T1 getValue1() {
        return value1;
    }

    public T2 getValue2() {
        return value2;
    }

    public void setValue1(T1 v1) {
        value1 = v1;
    }

    public void setValue2(T2 v2) {
        value2 = v2;
    }
}
