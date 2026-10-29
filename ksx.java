import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ksx {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	public static void main(String[] args) {
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.print("Enter a city: ");
			String city = scanner.nextLine().trim();
			if (city.isEmpty()) {
				System.out.println("Please enter a city name.");
				return;
			}

			String query = URLEncoder.encode(city, StandardCharsets.UTF_8);
			String locationJson = get("https://geocoding-api.open-meteo.com/v1/search?name="
					+ query + "&count=1&language=en&format=json");
			Matcher location = Pattern.compile(
					"\\{\\s*\"id\"\\s*:\\s*\\d+.*?\"name\"\\s*:\\s*\"([^\"]+)\".*?"
							+ "\"latitude\"\\s*:\\s*(-?[0-9.]+).*?\"longitude\"\\s*:\\s*(-?[0-9.]+)",
					Pattern.DOTALL).matcher(locationJson);
			if (!location.find()) {
				System.out.println("City not found. Check the spelling and try again.");
				return;
			}

			String place = location.group(1);
			String weatherJson = get("https://api.open-meteo.com/v1/forecast?latitude="
					+ location.group(2) + "&longitude=" + location.group(3)
					+ "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
					+ "precipitation,weather_code,wind_speed_10m&temperature_unit=celsius"
					+ "&wind_speed_unit=kmh&timezone=auto");
			String current = objectValue(weatherJson, "current");

			System.out.println("\nWeather for " + place);
			System.out.println("Updated: " + stringValue(current, "time"));
			System.out.println("Conditions: " + describeWeather((int) numberValue(current, "weather_code")));
			System.out.printf("Temperature: %.1f °C (feels like %.1f °C)%n",
					numberValue(current, "temperature_2m"), numberValue(current, "apparent_temperature"));
			System.out.printf("Humidity: %.0f%%%n", numberValue(current, "relative_humidity_2m"));
			System.out.printf("Wind: %.1f km/h%n", numberValue(current, "wind_speed_10m"));
			System.out.printf("Precipitation: %.1f mm%n", numberValue(current, "precipitation"));
		} catch (Exception e) {
			System.out.println("Couldn't retrieve weather. Check your internet connection and try again.");
		}
	}

	private static String get(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Weather service returned HTTP " + response.statusCode());
		}
		return response.body();
	}

	private static String objectValue(String json, String key) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\{[^}]*})").matcher(json);
		if (!matcher.find()) throw new IllegalStateException("Missing " + key + " data");
		return matcher.group(1);
	}

	private static double numberValue(String json, String key) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
				+ "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
		if (!matcher.find()) throw new IllegalStateException("Missing " + key + " value");
		return Double.parseDouble(matcher.group(1));
	}

	private static String stringValue(String json, String key) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
				.matcher(json);
		return matcher.find() ? matcher.group(1) : "N/A";
	}

	private static String describeWeather(int code) {
		switch (code) {
			case 0: return "Clear sky";
			case 1: return "Mainly clear";
			case 2: return "Partly cloudy";
			case 3: return "Overcast";
			case 45: case 48: return "Fog";
			case 51: case 53: case 55: return "Drizzle";
			case 56: case 57: return "Freezing drizzle";
			case 61: case 63: case 65: return "Rain";
			case 66: case 67: return "Freezing rain";
			case 71: case 73: case 75: case 77: return "Snow";
			case 80: case 81: case 82: return "Rain showers";
			case 85: case 86: return "Snow showers";
			case 95: return "Thunderstorm";
			case 96: case 99: return "Thunderstorm with hail";
			default: return "Unknown conditions";
		}
	}
}
