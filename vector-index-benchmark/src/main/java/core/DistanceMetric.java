package core;

public class DistanceMetric {
    public static float cosineDistance(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same length.");
        }
        float product = 0.0f;
        for (int i = 0; i < vector1.length; i ++) {
            product += vector1[i] * vector2[i];
        }
        return 1.0f - product;
    }

    public float euclideanDistance(float[] vector1, float [] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same length.");
        }
        float sum = 0;
        for (int i = 0; i < vector1.length; i++) {
            float diff = vector1[i] - vector2[i];
            sum += diff*diff;
        }
        return (float) Math.sqrt(sum);
    }

    public float calculateDistance(float[] query, float[] data, String dataset) {
        return switch (dataset) {
            case "random" -> cosineDistance(query, data);
            case "sift" -> euclideanDistance(query, data);
            default -> 0.0f;
        };
    }
}
