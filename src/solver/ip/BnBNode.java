package solver.ip;
import java.util.*;

public class BnBNode implements Comparable<BnBNode> {
        private double value;
        private double[] solution;
        private Map<Integer, Integer> constraints;

        public BnBNode(double value, double[] solution, Map<Integer, Integer> constraints) {
            this.value = value;
            this.solution = solution.clone();
            this.constraints = new HashMap<>(constraints);
        }

        public double getValue() {
            return value;
        }

        public double[] getSolution() {
            return solution.clone();
        }

        public Map<Integer, Integer> getConstraints() {
            return new HashMap<>(constraints);
        }

        @Override
        public int compareTo(BnBNode other) {
            return Double.compare(this.value, other.value);
        }
    }