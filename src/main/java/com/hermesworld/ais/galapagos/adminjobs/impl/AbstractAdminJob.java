package com.hermesworld.ais.galapagos.adminjobs.impl;

import com.hermesworld.ais.galapagos.adminjobs.AdminJob;

public abstract class AbstractAdminJob implements AdminJob {

    private final static int BANNER_WIDTH = 80;

    private final static int MIN_BANNER_PREFIX = 3;

    protected String banner(String text) {
        return banner(text, BANNER_WIDTH);
    }

    protected String banner(String text, int bannerWidth) {
        if (text.length() > (bannerWidth - (MIN_BANNER_PREFIX + 1) * 2)) {
            return "=== " + text + " ===";
        }
        int preLen = (bannerWidth - text.length()) / 2;
        int postLen = bannerWidth - text.length() - preLen;

        StringBuilder sb = new StringBuilder();
        if (text.isEmpty()) {
            return "=".repeat(bannerWidth);
        }
        sb.append("=".repeat(preLen - 1));
        sb.append(" ");
        sb.append(text);
        sb.append(" ");
        sb.append("=".repeat(postLen - 1));
        return sb.toString();
    }

    protected void printBanner(String text) {
        System.out.println();
        System.out.println(banner(text));
        System.out.println();
    }

    protected void printBanner(String text, int bannerWidth) {
        System.out.println();
        System.out.println(banner(text, bannerWidth));
        System.out.println();
    }

}
