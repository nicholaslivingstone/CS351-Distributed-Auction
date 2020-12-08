package com.bankserver;

public class SynchronizedCounter {
    private int c;

    public SynchronizedCounter(int c){
        this.c = c;
    }

    public synchronized int increment(){return c++;}
    public synchronized int value(){return c;}
}
