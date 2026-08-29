package com.sakarrobotics.c40agent.api.mqtt.dto;

/**
 * One telemetry reading. Field names are Gson's default (no {@code
 * @SerializedName} needed) and are deliberately identical to the Sakar
 * Cloud backend's {@code TelemetryReading} record component names
 * (metric/valueText/valueNumeric/recordedAt) so the JSON this produces
 * deserializes into that record with no field mapping on either side.
 */
public final class TelemetryFieldReading {

    private final String metric;
    private final String valueText;
    private final Double valueNumeric;
    private final String recordedAt;

    public TelemetryFieldReading(String metric, String valueText, Double valueNumeric, String recordedAt) {
        this.metric = metric;
        this.valueText = valueText;
        this.valueNumeric = valueNumeric;
        this.recordedAt = recordedAt;
    }

    public String getMetric() {
        return metric;
    }

    public String getValueText() {
        return valueText;
    }

    public Double getValueNumeric() {
        return valueNumeric;
    }

    public String getRecordedAt() {
        return recordedAt;
    }
}
