package dataset;

import core.Vector;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DatasetLoader {
    public static List<Vector> loadFVectors(String filePath) throws IOException {
        List<Vector> vectors = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            BufferedInputStream bis = new BufferedInputStream(fis);
            int vectorId = 0;

            while (bis.available() > 0) {
                byte [] dimBytes = new byte[4];
                var ignored = bis.read(dimBytes);
                ByteBuffer dimBuffer = ByteBuffer.wrap(dimBytes).order(ByteOrder.LITTLE_ENDIAN);
                int dimension = dimBuffer.getInt();

                // read vector data (dimension * 4 bytes, little-endian)
                byte [] vectorBytes = new byte[dimension * 4];
                var ignored2 = bis.read(vectorBytes);
                ByteBuffer vectorBuffer = ByteBuffer.wrap(vectorBytes).order(ByteOrder.LITTLE_ENDIAN);

                float [] data = new float[dimension];
                for (int i = 0; i < dimension; i++) {
                    data[i] = vectorBuffer.getFloat();
                }

                vectors.add(new Vector("cohere_" + vectorId, data));
                vectorId++;
            }

            bis.close();
        }
        return vectors;
    }

    public static List<int[]> loadIVecs(String filepath) throws IOException {
        List<int []> groundTruth = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filepath)) {
            BufferedInputStream bis = new BufferedInputStream(fis);

            while (bis.available() > 0) {
                byte [] dimBytes = new byte[4];
                int bytesRead = bis.read(dimBytes);
                if (bytesRead==-1) break;

                ByteBuffer dimBuffer = ByteBuffer.wrap(dimBytes).order(ByteOrder.LITTLE_ENDIAN);
                int dimension = dimBuffer.getInt();

                // read vector data (dimension * 4 bytes, little-endian)
                byte [] idsBytes = new byte[dimension * 4];
                var ignored2 = bis.read(idsBytes);
                ByteBuffer idsBuffer = ByteBuffer.wrap(idsBytes).order(ByteOrder.LITTLE_ENDIAN);

                int [] neighbors = new int[dimension];
                for (int i = 0; i < dimension; i++) {
                    neighbors[i] = idsBuffer.getInt();
                }

                groundTruth.add(neighbors);
            }

            bis.close();
        }
        return groundTruth;
    }

    public static List<Vector> loadHDF5Vectors(String filepath, String datasetName) throws IOException {
        List<Vector> vectors = new ArrayList<>();

        try (HdfFile hdfFile = new HdfFile(Paths.get(filepath))) {
            Dataset dataset = hdfFile.getDatasetByPath(datasetName);
            float[][] data = (float[][]) dataset.getData();

            for (int i = 0; i < data.length; i++) {
                vectors.add(new Vector("vec_" + i, data[i]));
            }
        }

        return vectors;
    }

    public static List<int[]> loadHDF5GroundTruth(String filepath, String datasetName) throws IOException {
        List<int[]> groundTruth = new ArrayList<>();

        try (HdfFile hdfFile = new HdfFile(Paths.get(filepath))) {
            Dataset dataset = hdfFile.getDatasetByPath(datasetName);
            int[][] data = (int[][]) dataset.getData();

            for (int[] neighbors : data) {
                groundTruth.add(neighbors);
            }
        }

        return groundTruth;
    }
}
