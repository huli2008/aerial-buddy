package com.aerial.buddy;

/** Shared port for the TV mirror stream (kept tiny so MirrorActivity needs no extra imports). */
public final class MirrorServicePort {
    public static final int PORT = 8768;

    private MirrorServicePort() {
    }
}