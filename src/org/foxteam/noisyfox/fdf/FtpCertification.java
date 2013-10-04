package org.foxteam.noisyfox.fdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午10:30
 * To change this template use File | Settings | File Templates.
 */
public final class FtpCertification {

    private final String certContext;
    private final static String algorithm = "SHA";
    private final static Random generator = new Random(System.currentTimeMillis());//随机数

    public FtpCertification(String certFile) throws IOException {
        this(new File(certFile));
    }

    public FtpCertification(File certFile) throws IOException {
        BufferedReader fr = new BufferedReader(new FileReader(certFile));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = fr.readLine()) != null) {
            sb.append(s);
        }
        certContext = sb.toString();
    }

    public boolean verify(String challenge, String response) {

        return response.equals(respond(challenge));
    }

    public String respond(String challenge) {
        byte[] certBytes = certContext.getBytes();
        byte[] challengeBytes = challenge.getBytes();
        for (int i = 0; i < certBytes.length; i++) {
            certBytes[i] ^= challengeBytes[i % challengeBytes.length];
        }

        return FtpUtil.hashBytes(certBytes, algorithm);
    }

    public static String generateChallenge() {
        byte[] challengeByte = new byte[32];//256位
        generator.nextBytes(challengeByte);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : challengeByte) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
