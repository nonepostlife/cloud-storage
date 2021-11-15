package ru.postlife.java.tools;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Scanner;

public class CryptPassword {
    private static int workload = 12;

    public static void main(String[] args) {
        String salt = BCrypt.gensalt(workload);
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter password: ");
        String in = scanner.next();
        System.out.print("Crypt password: ");
        System.out.print(BCrypt.hashpw(in, salt));
    }
}
