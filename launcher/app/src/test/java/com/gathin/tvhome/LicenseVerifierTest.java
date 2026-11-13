package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LicenseVerifierTest {
    private static final long NOW = 2_000_000_000_000L;

    private static final String VALID =
            "eyJ2IjoxLCJwcm9kdWN0IjoiQ1lUViIsImxpY2Vuc2VfaWQiOiJURVNULVIxMTgiLCJpc3N1ZWRfYXQiOjE3MDAwMDAwMDAwMDAsImV4cGlyZXNfYXQiOjQxMDI0NDQ4MDAwMDB9." +
            "FPBboVIUBucSiT-xd_cnnIxXyQ5T4fQ68WymHFUMniSsWaT_aUnrQywxnMHsiqh-A9wmvxtuTPs2B8PEJubFqtARYL7rXQoTrAxf5SezudUSy0KuWqxX5Bk04MSKfTHpV-fpHN_wjWQKTMFWBgeIVQC2mbZ_bfs6N1a7Y8mm3jHUdsLbXyukyKuSo6EWky2R-AuDOqZC0aOv-w33RNniWQxhQ3yGjTIir-9N-gekrF4XYdxrJ4-6FoFOLAbp2SfWhkNlHveg1BR0_HMuCSfaz_xXDI9MMnpGaC7iT4KTar3D92Df6D6O6Pv27cYs_uFJrdTq29mHzXC7B7-4iMDltqE23erYnuMm0bzZOXDSLGvTAS6ENU38IINHyVSHSh3UlhvXahEU38hfJhlMNksRWcgnz6b2s8289XE6c9qdECftW8TJ7cat0ZOzvykNRob2VRgdJyvx4B1skeQG4KI1Zo0Uef7IWJRupVXIlTokIBfGCVJBBH6a0UO0DEifG8yw";

    private static final String VALID_SECOND =
            "eyJ2IjoxLCJwcm9kdWN0IjoiQ1lUViIsImxpY2Vuc2VfaWQiOiJURVNULVNIQVJFRC1TRUNPTkQiLCJpc3N1ZWRfYXQiOjE3MDAwMDAwMDAwMDAsImV4cGlyZXNfYXQiOjQxMDI0NDQ4MDAwMDB9." +
            "Ni9RDUpUf3ul7W4G0Jvd4QFK6s4RiZZ5ph9BvRuJgfe-pBWFyup1sBmsCGIXlyIPiQEVadBAMKKfPMMPAzM--4CY7MN2WN0e9vj1m1lsJSRhamhx-aO81LU4_EL0Wdwww59GVsQu7BdTPRX89edHHLkZV_NWs9F7sUMu_uKWTT3JxDArg86Bl8Jza5UFOB-UDjJE5Y2MRn2SveqSt6cC1Kh39PSmDduarrQ3GIXzvjgIyQhX_Qjp_e_kY1ij0UvD1PlJIYjrhbW0bhrB6MVqWPFdFaSxhSUb3uUY16nogTTMqOnlzHt1-PktqtpjxLQp8drrObjkSLYqaJrZGCc4-T_aAdqaq_vnnmWc6O-J6XGXwBA6Z5mecmPBP5nYgj4bVpC17oWYkhgERBPiH2RQKx-RH2uerjlMIOjU71wD5IkAKIMBGz8kCi046eM9OIVTUFyPvPzvS5gsAQJZlsz8z5ID9NonGs7aeVvy7J4oCekmAurkkwVGNIkhswaWgbqC";

    private static final String EXPIRED =
            "eyJ2IjoxLCJwcm9kdWN0IjoiQ1lUViIsImxpY2Vuc2VfaWQiOiJURVNULUVYUElSRUQiLCJpc3N1ZWRfYXQiOjE2MDAwMDAwMDAwMDAsImV4cGlyZXNfYXQiOjE3MDAwMDAwMDAwMDB9." +
            "Rd0OeTFAlT52v99F22Rl12EnszT0Y1k19XtTZiUvzuPrt4zI_aMjC9krw9uYkusBnLBdB57QKecCw1l4WHgW3FRh3oKY7DD2eGqs83KoRH6iEy0n7bWJDYVpHK9g_OCYvqP4t9dwgsaW7TGjILSpf6pUbhqY1P2CpEIDmYTGUpvnqVhBKa9y0fncucCikLdYGoRAv34vSkAKeTOvZ_h42HZj-Wchg-Dk1jMbqabK83cK0wuSGBAGNRWpgNqY5PDXZWz1f5cbN7LW_z04UvtRoVzlJTbY2rMcdUfHhPBViGapx-5QtP4-sUA32VFhli8b-JVDH4KsF1RvAtiva2xT-c1hIvEFcU9zJ1eRB00dDQvIWsaQBeIY97fdzi0pPEKnOJTxEjJha0B1MDEnbgvBhPKEZPcabiLeJdxC9MoMFxcf056fpLZFgVacRYbG6g_NX2u4jkxyWRBYGJ0gBmBdzDNmetzUquXBf7evK69as8uluEYcdK3CxWCFVu6L3R9n";

    private static final String WRONG_PRODUCT =
            "eyJ2IjoxLCJwcm9kdWN0IjoiT1RIRVIiLCJsaWNlbnNlX2lkIjoiVEVTVC1XUk9ORyIsImlzc3VlZF9hdCI6MTcwMDAwMDAwMDAwMCwiZXhwaXJlc19hdCI6NDEwMjQ0NDgwMDAwMH0." +
            "rKlUUDPg6SfvSUwYXBHR8qe2pz-E8FkKJJ01UKyfMy8myVlOTO8bigYnXcZfHtPCuioOpHKgX6F2eQPUub5WwzRM3VgY6VCKjMuBpp4WAG1EX1XSPnbliSstIvD5bCrUl1GjMjMrQvZUoanPW7taD482bSJzZnLRClEqvVHDjRjFCc3yJuY6bro0rerWiSwZ-nyzM7Dy7HW0tAbj3MhbnLhpf7f6RSD1H_9XjMydhb3m77aW8v3lxJJp0lgMCf9LjPi6L39aatGrdNNtCUIPCb8HJIG2shclPh3jjK2Uw6SMJBbbnO4cFiaWud0IegFcYKkulKGnXhlM73UUYURyadzAFk9iW0omOIw831F2f86aXV5u_jqKaNZmuqXqFsBcmJpg9zKLBWFJCkKRwl993brf_x20vXR_VFjhuAoniLgmVuUncO_-I1SsHS3Gz1L9yy2Xqc_hFldTPWw4rQEoFxA9Fcw5Q8uwB4BZbNHLwjC0XBY-QFdqxhUkjLHUTheO";

    @Test public void rejectsPlaintextSharedCode() {
        assertEquals(LicenseVerifier.Status.MALFORMED,
                LicenseVerifier.verify("CYTV0822", NOW).status);
    }

    @Test public void acceptsValidOfflineCode() {
        LicenseVerifier.Result result = LicenseVerifier.verify(VALID, NOW);
        assertTrue(result.isValid());
        assertEquals("TEST-R118", result.licenseId);
    }

    @Test public void acceptsAnotherIndependentlyIssuedSharedCode() {
        LicenseVerifier.Result first = LicenseVerifier.verify(VALID, NOW);
        LicenseVerifier.Result second = LicenseVerifier.verify(VALID_SECOND, NOW);
        assertTrue(first.isValid());
        assertTrue(second.isValid());
        assertEquals("TEST-R118", first.licenseId);
        assertEquals("TEST-SHARED-SECOND", second.licenseId);
    }

    @Test public void rejectsExpiredCode() {
        assertEquals(LicenseVerifier.Status.EXPIRED,
                LicenseVerifier.verify(EXPIRED, NOW).status);
    }

    @Test public void rejectsWrongProduct() {
        assertEquals(LicenseVerifier.Status.WRONG_PRODUCT,
                LicenseVerifier.verify(WRONG_PRODUCT, NOW).status);
    }

    @Test public void rejectsAlteredSignatureAndMalformedInput() {
        assertEquals(LicenseVerifier.Status.MALFORMED, LicenseVerifier.verify("not-a-code", NOW).status);
        assertEquals(LicenseVerifier.Status.BAD_SIGNATURE,
                LicenseVerifier.verify(VALID.substring(0, VALID.length() - 1) + "A", NOW).status);
    }
}
