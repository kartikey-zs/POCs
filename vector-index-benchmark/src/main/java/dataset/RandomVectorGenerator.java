package dataset;

import core.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomVectorGenerator {
    long seed;
    private final Random random;
    private int nextId = 0;

    public RandomVectorGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public List<Vector> generate (int count, int dimensions) {
        List<Vector> vectorList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float [] vector = new float[dimensions];
            for (int d = 0; d < dimensions; d++) {
                vector[d] = (float) random.nextGaussian();
            }
            double magnitude = calculateMagnitude(vector);
            for (int dim = 0; dim < dimensions; dim ++) {
                vector[dim] /= (float) magnitude;
            }
            Vector v = new Vector("vec_" + nextId, vector);
            nextId++;
            vectorList.add(v);
        }
        return vectorList;
    }

    private double calculateMagnitude(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += Math.pow(v, 2);
        }
        return Math.sqrt(sum);
    }
}
