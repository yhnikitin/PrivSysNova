package pt.unl.fct.di.syspriv.entities;

import java.util.*;

public class KAnonymityTool<T extends Anonymizable> {

    private final int k;
    private final float t;

    public KAnonymityTool(int k, float t) {
        this.k = k;
        this.t = t;
    }

    public boolean isKAnonymous(List<T> records) {
        Map<Map<String, Object>, Integer> groupCounts = new HashMap<>();
        for (T record : records) {
            Map<String, Object> qiValues = record.getQuasiIdentifiers();
            groupCounts.put(qiValues, groupCounts.getOrDefault(qiValues, 0) + 1);
        }
        return groupCounts.values().stream().allMatch(count -> count >= k);
    }

    public boolean isTClose(List<T> P, List<T> Q) {
        if (P.isEmpty() || Q.isEmpty()) {
            return false;
        }

        Map<Double, Integer> pCounts = sensitiveValueCounts(P);
        Map<Double, Integer> qCounts = sensitiveValueCounts(Q);
        
        TreeSet<Double> orderedValues = new TreeSet<>(qCounts.keySet());
        if (!orderedValues.containsAll(pCounts.keySet())) {
            throw new IllegalArgumentException("P must contain only sensitive values present in Q");
        }

        if (orderedValues.size() <= 1) {
            return true;
        }

        List<Double> values = List.copyOf(orderedValues);
        double cumulativeDifference = 0.0;
        double cumulativeDifferenceSum = 0.0;
        
        for (int index = 0; index < values.size() - 1; index++) {
            double value = values.get(index);
            double pProbability = pCounts.getOrDefault(value, 0) / (double) P.size();
            double qProbability = qCounts.getOrDefault(value, 0) / (double) Q.size();
            
            cumulativeDifference += qProbability - pProbability;
            cumulativeDifferenceSum += Math.abs(cumulativeDifference);
        }

        double earthMoverDistance = cumulativeDifferenceSum / (values.size() - 1);
        return earthMoverDistance <= t;
    }

    private Map<Double, Integer> sensitiveValueCounts(List<T> records) {
        Map<Double, Integer> counts = new HashMap<>();
        
        for (T record : records) {
            Object rawValue = record.getSensitiveValue();
            Double doubleValue;
            
            if (rawValue instanceof Number) {
                doubleValue = ((Number) rawValue).doubleValue();
            } else if (rawValue instanceof String) {
                try {
                    doubleValue = Double.valueOf((String) rawValue);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Cannot parse sensitive value as number: " + rawValue);
                }
            } else {
                throw new IllegalArgumentException("Unsupported sensitive value type.");
            }

            counts.put(doubleValue, counts.getOrDefault(doubleValue, 0) + 1);
        }
        
        return counts;
    }

    public Map<Map<String, Object>, Set<Object>> sensitiveValuesPerGroup(List<T> records) {
        Map<Map<String, Object>, Set<Object>> groupedValues = new HashMap<>();
        for (T record : records) {
            Map<String, Object> groupKey = new HashMap<>(record.getQuasiIdentifiers());
            groupedValues.computeIfAbsent(groupKey, ignored -> new HashSet<>())
                    .add(record.getSensitiveValue());
        }
        return groupedValues;
    }

    // Retained for backward compatibility
    public List<T> runMondrain(List<T> records) {
        return runMondrian(records, true);
    }

    // 1st Option: Run Mondrian WITH t-closeness
    public List<T> runMondrianWithTCloseness(List<T> records) {
        return runMondrian(records, true);
    }

    // 2nd Option: Run Mondrian WITHOUT t-closeness (only k-anonymity)
    public List<T> runMondrianWithoutTCloseness(List<T> records) {
        return runMondrian(records, false);
    }

    // Core method that handles both depending on the flag
    private List<T> runMondrian(List<T> records, boolean enforceTCloseness) {
        if (records.size() < k) {
            return List.of();
        }

        List<List<T>> partitions = new ArrayList<>();
        splitPartition(new ArrayList<>(records), records, partitions, enforceTCloseness);

        List<T> anonymized = new ArrayList<>();
        for (List<T> partition : partitions) {
            anonymizePartition(partition);
            anonymized.addAll(partition);
        }
        return anonymized;
    }

    private void splitPartition(List<T> currentPartition, List<T> originalRecords, List<List<T>> partitions, boolean enforceTCloseness) {
        if (currentPartition.size() < k) {
            return;
        }

        String attributeToSplit = findAttributeWithLargestRange(currentPartition);
        if (attributeToSplit == null) {
            partitions.add(currentPartition);
            return;
        }

        currentPartition.sort(Comparator.comparingDouble(record -> ((Number) record.getQuasiIdentifiers().get(attributeToSplit)).doubleValue()));

        int splitIndex = findValidSplit(currentPartition, attributeToSplit, originalRecords, enforceTCloseness);
        if (splitIndex < 0) {
            partitions.add(currentPartition);
            return;
        }

        List<T> leftPartition = new ArrayList<>(currentPartition.subList(0, splitIndex));
        List<T> rightPartition = new ArrayList<>(currentPartition.subList(splitIndex, currentPartition.size()));
        splitPartition(leftPartition, originalRecords, partitions, enforceTCloseness);
        splitPartition(rightPartition, originalRecords, partitions, enforceTCloseness);
    }

    // Searches for a valid index to split the partition.
    private int findValidSplit(List<T> sortedPartition, String attribute, List<T> originalRecords, boolean enforceTCloseness) {
        int size = sortedPartition.size();
        int medianIndex = size / 2;

        for (int offset = 0; offset <= size / 2; offset++) {
            
            // Check the candidate to the right of the median
            int rightCandidate = medianIndex + offset;
            if (isValidCut(rightCandidate, size, sortedPartition, attribute, originalRecords, enforceTCloseness)) {
                return rightCandidate;
            }

            // Check the candidate to the left of the median
            int leftCandidate = medianIndex - offset;
            if (isValidCut(leftCandidate, size, sortedPartition, attribute, originalRecords, enforceTCloseness)) {
                return leftCandidate;
            }
        }
        return -1;
    }

    private boolean isValidCut(int candidateIndex, int size, List<T> sortedPartition, String attribute, List<T> originalRecords, boolean enforceTCloseness) {
        // Base k-anonymity validation
        if (candidateIndex < k || candidateIndex > size - k) {
            return false;
        }

        // We cannot split records that share the exact same quasi-identifier value.
        double leftValue = ((Number) sortedPartition.get(candidateIndex - 1).getQuasiIdentifiers().get(attribute)).doubleValue();
        double rightValue = ((Number) sortedPartition.get(candidateIndex).getQuasiIdentifiers().get(attribute)).doubleValue();        
        if (leftValue >= rightValue) {
            return false;
        }

        if (!enforceTCloseness) {
            return true;
        }

        // If we DO need t-closeness, check both halves against the original distribution
        List<T> leftHalf = sortedPartition.subList(0, candidateIndex);
        List<T> rightHalf = sortedPartition.subList(candidateIndex, size);

        return isTClose(leftHalf, originalRecords) && isTClose(rightHalf, originalRecords);
    }

    private String findAttributeWithLargestRange(List<T> records) {
        Map<String, List<Float>> attributeRanges = new HashMap<>();

        for (T record : records) {
            Map<String, Object> qiValues = record.getQuasiIdentifiers();
            for (Map.Entry<String, Object> entry : qiValues.entrySet()) {
                String attribute = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Number) {
                    float numericValue = ((Number) value).floatValue();
                    if(!attributeRanges.containsKey(attribute)) {
                        attributeRanges.put(attribute, new ArrayList<>(List.of(numericValue, numericValue)));
                    }
                    List<Float> range = attributeRanges.get(attribute);
                    if(numericValue < range.get(0)) {
                        range.set(0, numericValue);
                    } else if(numericValue > range.get(1)) {
                        range.set(1, numericValue);
                    }
                }
            }
        }

        String attributeWithLargestRange = null;
        float largestRange = -1;
        for (Map.Entry<String, List<Float>> entry : attributeRanges.entrySet()) {
            String attribute = entry.getKey();
            List<Float> range = entry.getValue();
            float currentRange = range.get(1) - range.get(0);
            if (currentRange > largestRange) {
                largestRange = currentRange;
                attributeWithLargestRange = attribute;
            }
        }
        return attributeWithLargestRange;
    }

    private void anonymizePartition(List<T> partition) {
        if (partition.isEmpty()) return;

        Set<String> attributes = partition.get(0).getQuasiIdentifiers().keySet();
        Map<String, Object> generalizedValues = new HashMap<>();

        for (String attr : attributes) {
            Object firstValue = partition.get(0).getQuasiIdentifiers().get(attr);
            if (firstValue instanceof Number) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (T record : partition) {
                    double value = ((Number) record.getQuasiIdentifiers().get(attr)).doubleValue();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
                generalizedValues.put(attr, (float) ((min + max) / 2.0));
                } else {
                boolean sameValue = partition.stream().allMatch(record ->
                        firstValue.equals(record.getQuasiIdentifiers().get(attr)));
                generalizedValues.put(attr, sameValue ? firstValue : "*");
            }
        }

        for (T record : partition) {
            record.setQuasiIdentifiers(new HashMap<>(generalizedValues));
        }
    }
}