package com.gathin.tvhome;

/** Deterministic representative coordinates for Taiwan's 22 counties and cities. */
final class WeeklyWeatherCity {
    final String displayName;
    final double latitude;
    final double longitude;

    private WeeklyWeatherCity(String displayName, double latitude, double longitude) {
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private static final String[][] CITIES = {
            {"桃園市", "桃園", "24.9936", "121.3010"},
            {"台北市", "台北", "25.0330", "121.5654"},
            {"新北市", "新北", "25.0169", "121.4628"},
            {"基隆市", "基隆", "25.1276", "121.7392"},
            {"新竹市", "24.8138", "120.9675"},
            {"新竹縣", "24.8392", "121.0177"},
            {"苗栗縣", "苗栗", "24.5602", "120.8214"},
            {"台中市", "台中", "24.1477", "120.6736"},
            {"彰化縣", "彰化", "24.0747", "120.5383"},
            {"南投縣", "南投", "23.9157", "120.6850"},
            {"雲林縣", "雲林", "23.7092", "120.4313"},
            {"嘉義市", "23.4801", "120.4491"},
            {"嘉義縣", "23.4589", "120.5740"},
            {"台南市", "台南", "22.9999", "120.2269"},
            {"高雄市", "高雄", "22.6273", "120.3014"},
            {"屏東縣", "屏東", "22.6761", "120.4942"},
            {"宜蘭縣", "宜蘭", "24.7021", "121.7378"},
            {"花蓮縣", "花蓮", "23.9872", "121.6015"},
            {"台東縣", "台東", "22.7972", "121.0714"},
            {"澎湖縣", "澎湖", "23.5711", "119.5793"},
            {"金門縣", "金門", "24.4321", "118.3171"},
            {"連江縣", "連江", "26.1605", "119.9517"}
    };

    private WeeklyWeatherCity(String[] row) {
        this(row[0], Double.parseDouble(row[row.length - 2]), Double.parseDouble(row[row.length - 1]));
    }

    /** Returns a fixed county/city centre only for an exact supported name; districts use geocoding. */
    static WeeklyWeatherCity find(String input) {
        if (input == null) return null;
        String city = input.trim().replace("臺", "台");
        for (String[] row : CITIES) {
            if (city.equals(row[0].replace("臺", "台")) || (row.length == 4 && city.equals(row[1]))) {
                return new WeeklyWeatherCity(row);
            }
        }
        return null;
    }
}
