package com.bankserver;

public class BankAccount {
    protected Long balance;

    public BankAccount(Long balance){
        this.balance = balance;
    }

    public synchronized Long getBalance(){
        return balance;
    }

    public synchronized Long deposit(Long value){
        balance += value;
        return balance;

    }

    public synchronized Long withdraw(Long value) {
        if(balance - value < 0){
            return Long.valueOf(-1);
        }
        else{
            balance -= value;
        }

        return balance;
    }
}
