package com.example.productauth.service;

import com.example.productauth.domain.GeoIpLocation;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);
    private final DatabaseReader reader;

    public GeoIpService(@Value("${app.geoip.database-path:${GEOIP_DATABASE_PATH:}}") String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            reader = null;
            log.info("GeoIP enrichment is disabled: GEOIP_DATABASE_PATH is not configured.");
            return;
        }

        try {
            Path path = Path.of(databasePath);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalStateException("Configured GeoIP database is missing or unreadable.");
            }
            DatabaseReader loaded = new DatabaseReader.Builder(path.toFile()).build();
            String databaseType = loaded.getMetadata().getDatabaseType();
            if (databaseType == null || !databaseType.toLowerCase(Locale.ROOT).contains("city")) {
                loaded.close();
                throw new IllegalStateException("Configured GeoIP database is not a City database.");
            }
            reader = loaded;
            log.info("Local GeoIP enrichment enabled.");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not open the configured local GeoIP City database.", exception);
        }
    }

    public Optional<GeoIpLocation> lookup(String ipAddress) {
        if (reader == null) {
            return Optional.empty();
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            log.debug("GeoIP lookup skipped: client IP is unavailable.");
            return Optional.empty();
        }

        final InetAddress address;
        try {
            address = parseLiteralAddress(ipAddress.trim());
        } catch (IllegalArgumentException exception) {
            log.info("GeoIP lookup skipped: client address is not a valid IP literal.");
            return Optional.empty();
        }
        if (!isPublicAddress(address.getAddress())) {
            log.info("GeoIP lookup skipped: client address is local, private, or reserved.");
            return Optional.empty();
        }

        try {
            CityResponse response = reader.city(address);
            Double latitude = response.getLocation().getLatitude();
            Double longitude = response.getLocation().getLongitude();
            String country = response.getCountry() == null ? null : response.getCountry().getName();
            String countryIsoCode = response.getCountry() == null
                    ? null : response.getCountry().getIsoCode();
            String region = response.getMostSpecificSubdivision() == null
                    ? null : response.getMostSpecificSubdivision().getName();
            String city = response.getCity() == null ? null : response.getCity().getName();
            if (country == null && region == null && city == null
                    && (latitude == null || longitude == null)) {
                log.debug("GeoIP database returned no location data for the client address.");
                return Optional.empty();
            }
            return Optional.of(new GeoIpLocation(country, countryIsoCode, region, city, latitude, longitude));
        } catch (IOException | GeoIp2Exception | RuntimeException exception) {
            log.warn("Local GeoIP lookup failed; scan verification will continue without location.");
            return Optional.empty();
        }
    }

    @PreDestroy
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }

    static InetAddress parseLiteralAddress(String value) {
        if (value.indexOf(':') < 0) {
            String[] octets = value.split("\\.", -1);
            if (octets.length != 4) {
                throw new IllegalArgumentException("Not an IPv4 literal.");
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < octets.length; i++) {
                if (!octets[i].matches("\\d{1,3}")) {
                    throw new IllegalArgumentException("Not an IPv4 literal.");
                }
                if (octets[i].length() > 1 && octets[i].startsWith("0")) {
                    throw new IllegalArgumentException("Not an unambiguous IPv4 literal.");
                }
                int octet = Integer.parseInt(octets[i]);
                if (octet > 255) {
                    throw new IllegalArgumentException("Not an IPv4 literal.");
                }
                bytes[i] = (byte) octet;
            }
            try {
                return InetAddress.getByAddress(bytes);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Not an IPv4 literal.", exception);
            }
        }
        if (!value.matches("[0-9a-fA-F:.]+")) {
            throw new IllegalArgumentException("Not an IPv6 literal.");
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address.getAddress().length != 16) {
                throw new IllegalArgumentException("Not an IPv6 literal.");
            }
            return address;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Not an IPv6 literal.", exception);
        }
    }

    static boolean isPublicAddress(byte[] address) {
        if (address.length == 4) {
            int first = address[0] & 0xff;
            int second = address[1] & 0xff;
            int third = address[2] & 0xff;
            return !(first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && (second == 168
                    || second == 0 && (third == 0 || third == 2)
                    || second == 88 && third == 99))
                    || first == 198 && (second == 18 || second == 19)
                    || first == 198 && second == 51 && third == 100
                    || first == 203 && second == 0 && third == 113);
        }

        boolean allZeroExceptLast = true;
        for (int i = 0; i < address.length - 1; i++) {
            allZeroExceptLast &= address[i] == 0;
        }
        boolean globalUnicast = (address[0] & 0xe0) == 0x20;
        boolean special2001 = (address[0] & 0xff) == 0x20
                && (address[1] & 0xff) == 0x01
                && (address[2] & 0xfe) == 0x00;
        boolean documentation = (address[0] & 0xff) == 0x3f
                && (address[1] & 0xff) == 0xff
                && (address[2] & 0xf0) == 0;
        return !(allZeroExceptLast
                || (address[0] == 0 && address[15] == 1)
                || (address[0] & 0xfe) == 0xfc
                || (address[0] & 0xff) == 0xfe && (address[1] & 0xc0) == 0x80
                || (address[0] & 0xff) == 0xff
                || !globalUnicast
                || special2001
                || (address[0] == 0x20 && address[1] == 0x01
                && address[2] == 0x0d && address[3] == (byte) 0xb8)
                || (address[0] == 0x20 && address[1] == 0x02)
                || documentation);
    }

}
