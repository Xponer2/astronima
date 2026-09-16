package play.xponer.astronima.sim;

/**
 * Water vapor behavior in a room.
 *
 * <p>Air holds only so much water before it condenses, and that ceiling falls steeply
 * as it cools — the Magnus formula gives the saturation vapor pressure. Players and
 * plants exhale vapor continuously, so a sealed habitat fogs up and then frosts on
 * cold walls unless something removes it. Relative humidity is the readout; anything
 * above saturation condenses out as liquid.
 */
public final class Humidity {
    /**
     * Saturation vapor pressure of water in kPa at {@code tempK} (Magnus, over liquid).
     *
     * <p>The Magnus denominator vanishes at −243.04 °C (≈30 K) and goes negative below
     * it, which would invert the curve; cryogenic rooms are clamped to effectively
     * zero instead. Water has no meaningful vapor pressure down there anyway.
     */
    public static double saturationPressureKPa(double tempK) {
        double tempC = tempK - 273.15;
        double denominator = tempC + 243.04;
        if (denominator <= 1.0) {
            return 0.0;
        }
        return 0.61094 * Math.exp(17.625 * tempC / denominator);
    }

    /** Relative humidity 0..1+ (values above 1 are supersaturated and will condense). */
    public static double relativeHumidity(RoomState room) {
        double saturation = saturationPressureKPa(room.temperatureK());
        if (saturation <= 0) {
            return 0;
        }
        return room.partialPressureKPa(Gas.WATER_VAPOR) / saturation;
    }

    /**
     * Condenses any vapor above saturation out of the room.
     *
     * @return moles of water condensed (liquid — reclaimable by a dehumidifier)
     */
    public static double condense(RoomState room) {
        double saturation = saturationPressureKPa(room.temperatureK());
        double excessKPa = room.partialPressureKPa(Gas.WATER_VAPOR) - saturation;
        if (excessKPa <= 0) {
            return 0;
        }
        // Below freezing this is deposition (frost) rather than dew, but the sim
        // only cares that the vapor leaves the air.
        double excessMoles = excessKPa * 1000.0 * room.volumeM3() / (GasMixture.R * room.temperatureK());
        return room.removeGas(Gas.WATER_VAPOR, excessMoles);
    }

    /** Mold thrives in warm, damp air — the ISS's recurring housekeeping problem. */
    public static boolean moldFavourable(RoomState room) {
        double tempC = room.temperatureK() - 273.15;
        return relativeHumidity(room) > 0.70 && tempC > 5.0 && tempC < 40.0;
    }

    private Humidity() {}
}
