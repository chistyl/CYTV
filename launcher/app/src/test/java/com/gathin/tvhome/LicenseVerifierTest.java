package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LicenseVerifierTest {
    private static final long NOW = 2_000_000_000_000L;
    private static final String DEVICE_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String DEVICE_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static final String VALID = "eyJ2IjoxLCJwcm9kdWN0IjoiQ1lUViIsImxpY2Vuc2VfaWQiOiJURVNULVZBTElEIiwiZGV2aWNlX2ZpbmdlcnByaW50IjoiQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQSIsImlzc3VlZF9hdCI6MTcwMDAwMDAwMDAwMCwiZXhwaXJlc19hdCI6NDEwMjQ0NDgwMDAwMH0.NJ0hv72UE__JdVFGuEzKSfI-5UoBbhVLnnIRNtez4UKdDT1nja6gWryTNFrfTVOtCyrume0Kzm1slH03NQzcMklay1HedDpi3BfQj6t1lJnOcR8sAymeTOc9SfNGLkqjCsa1ikWx6izFlIQGKS86pbUdEUAmzs1E7z2nwnmv-b330XlJ_wRwqlc41WMVSc4_S8N2MD3kl8sA8QPBTFG6k6QWfqG6NDrxSYyMCEO9eHbSKPCQ8kn6B3SXGC0kaCz5dmPIWtQQMdbypqKv9gHnMxtO1rbiqhX-uZrag550c92FRoafzfApaut3JpEAZ-nAThs389hKKPbF49jZtRHBNWBFcvpMMvxnFlVlQYjw3Gqg9-i6uiZDnJsKy0e6DimhroOizeutusPmPXmc40lVrGRF6YXx5XvfLEM7BJMAJ-JH_HaGVjrYoZiwnCVzsbn07DWxjrY4C8gsH8Ydz20lf3oipKPzEChtjxdga4XSlMO3KO2BYkn072gx_Pe8zhSb";
    private static final String EXPIRED = "eyJ2IjoxLCJwcm9kdWN0IjoiQ1lUViIsImxpY2Vuc2VfaWQiOiJURVNULUVYUElSRUQiLCJkZXZpY2VfZmluZ2VycHJpbnQiOiJBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBIiwiaXNzdWVkX2F0IjoxNjAwMDAwMDAwMDAwLCJleHBpcmVzX2F0IjoxNzAwMDAwMDAwMDAwfQ.F9khbb1xBlNCE7L7mimoo7tFTZQmbNUma1P_bh1kMcGGw04SXwaaMcpx3C_WNNG7FVumsjf2GiWf1EaBaZjVh2KXHEuxzi3yeELWKzJUBEa0uPWlO5rB1J25ji7u3V_QhCgo5gtiPdaC8TIOS9cllutSWtF2lYOZzSihDL6Ej5FdjlAEKQ1jhUQwX19Aoe-cJKwrmyb1nnuos7PpEQTeB1N0eyDi-CavyWyY-XnpKH7jHVZ5hNSF3Jp_T94tjziHTbfDfG_hAeXLJtL26Jhdk8iJ3vraNDzGLzYWLtP5V5A5UH0Qmr0xi-hB1f6bDWQKoyyGAs4IYtFU2bAiEA6RwYX84HT5Oa3_v8P0PXz_0Tjdmf1EerVDtMvLKFSh4BdiiAZ8k4mcmm6JB58cTtBhkfrxieb5B1OUFz4v2C5FkQpPspGSK3vt1ZHW-fjM2HN3hDmTwczHuD92LvZcvLFoBjPr9WCBxHp1bnB5WKdEdk8ebLOc7vNOyRcEcvAFD2js";
    private static final String WRONG_PRODUCT = "eyJ2IjoxLCJwcm9kdWN0IjoiT1RIRVIiLCJsaWNlbnNlX2lkIjoiVEVTVC1XUk9OR19QUk9EVUNUIiwiZGV2aWNlX2ZpbmdlcnByaW50IjoiQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQSIsImlzc3VlZF9hdCI6MTcwMDAwMDAwMDAwMCwiZXhwaXJlc19hdCI6NDEwMjQ0NDgwMDAwMH0.UCgUXJ3liZhEkDZiWDU-7JSMCEzealUPyPmn-RbvBpWbsSvFP2GhEVhFU-auN52dV8ASKXzPcQ_0lNm0L6HhVP5T6WmzNd3U5xxpQx8YEVE8F92GYXsTVG7w9ddIlgnYsOllQjuY1CSyLPqjSKUtvqvvx9vsxv9goa5BYuh8RthZztJvXbwh__-S004OIh68eP_BzIW7F4HzzM-1KfcKgFA67c4zfe8CuCiqOH94g0FNhBNzZekPCcT1a1OrKnK3yHc-rVQ8hM9fRD_KB800YzNgX1ltY_IIMF74vMqBQkVm7-SnpMLLPJHPLiPvpslWDvKkQapN54hZ1huTGUVwnfGyOdxN11pkaGE_oV3hptQadktIt1pCxVqcxwAxplaqhPgiv20MXdXW7nibBAhCN1Z44ImLzcYorBlInQPVx00PPCS2KVv_a8xwMc6avWNuT33qDjOGv2owww6g27hRQx1bqCTcLHNn3ONhA8pb-pt1NVKsbrD9IjMwYF5rJZMx";

    @Test public void acceptsCodeForMatchingDevice() {
        LicenseVerifier.Result result = LicenseVerifier.verify(VALID, DEVICE_A, NOW);
        assertTrue(result.isValid());
        assertEquals("TEST-VALID", result.licenseId);
    }

    @Test public void rejectsSameCodeOnAnotherDevice() {
        assertEquals(LicenseVerifier.Status.DEVICE_MISMATCH,
                LicenseVerifier.verify(VALID, DEVICE_B, NOW).status);
    }

    @Test public void rejectsCodeWithoutDeviceBinding() {
        assertEquals(LicenseVerifier.Status.DEVICE_MISMATCH,
                LicenseVerifier.verify(VALID, "", NOW).status);
    }

    @Test public void rejectsExpiredCode() {
        assertEquals(LicenseVerifier.Status.EXPIRED,
                LicenseVerifier.verify(EXPIRED, DEVICE_A, NOW).status);
    }

    @Test public void rejectsWrongProduct() {
        assertEquals(LicenseVerifier.Status.WRONG_PRODUCT,
                LicenseVerifier.verify(WRONG_PRODUCT, DEVICE_A, NOW).status);
    }

    @Test public void rejectsPlaintextMalformedAndAlteredCodes() {
        assertEquals(LicenseVerifier.Status.MALFORMED,
                LicenseVerifier.verify("CYTV0822", DEVICE_A, NOW).status);
        assertEquals(LicenseVerifier.Status.MALFORMED,
                LicenseVerifier.verify("not-a-code", DEVICE_A, NOW).status);
        assertEquals(LicenseVerifier.Status.BAD_SIGNATURE,
                LicenseVerifier.verify(VALID.substring(0, VALID.length() - 1) + "A", DEVICE_A, NOW).status);
    }
}
