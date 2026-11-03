package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeeklyWeatherCityTest {
    @Test
    public void resolvesEveryTaiwanCountyAndCity() {
        String[] names = {
                "桃園市", "台北市", "新北市", "基隆市", "新竹市", "新竹縣",
                "苗栗縣", "台中市", "彰化縣", "南投縣", "雲林縣", "嘉義市",
                "嘉義縣", "台南市", "高雄市", "屏東縣", "宜蘭縣", "花蓮縣",
                "台東縣", "澎湖縣", "金門縣", "連江縣"
        };
        for (String name : names) {
            assertNotNull("Missing fixed coordinate for " + name, WeeklyWeatherCity.find(name));
        }
    }

    @Test
    public void supportsTaiwanTraditionalAndShortAliases() {
        assertEquals("台北市", WeeklyWeatherCity.find("臺北市").displayName);
        assertEquals("桃園市", WeeklyWeatherCity.find("桃園").displayName);
        assertEquals("台中市", WeeklyWeatherCity.find("臺中").displayName);
        assertEquals("高雄市", WeeklyWeatherCity.find("高雄").displayName);
    }

    @Test
    public void leavesDistrictAndOverseasNamesForGeocoding() {
        assertNull(WeeklyWeatherCity.find("中壢區"));
        assertNull(WeeklyWeatherCity.find("東京"));
        assertNull(WeeklyWeatherCity.find("Osaka"));
    }

    @Test
    public void returnsValidCoordinates() {
        WeeklyWeatherCity city = WeeklyWeatherCity.find("桃園市");
        assertNotNull(city);
        assertEquals(24.9936, city.latitude, 0.0001);
        assertEquals(121.3010, city.longitude, 0.0001);
    }
}
