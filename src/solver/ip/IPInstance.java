package solver.ip;

import ilog.cplex.*;
import ilog.concert.*;

import java.util.*;

public class IPInstance implements Cloneable
{
    // IBM Ilog Cplex Solver 
    IloCplex cplex;

    int numTests;			// number of tests
    int numDiseases;		// number of diseases
    double[] costOfTest;  // [numTests] the cost of each test
    int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease
    double[] solution;
    Map<String, IloConstraint[]> constraintMap;
    IloNumVar[] isTestUsed;
    double objValue;
    int intObjValue = Integer.MAX_VALUE;
  
    public IPInstance()
    {
    super();
    }
  
    void init(int numTests, int numDiseases, double[] costOfTest, int[][] A) {
        assert(numTests >= 0) : "Init error: numtests should be non-negative " + numTests;
        assert(numDiseases >= 0) : "Init error: numtests should be non-negative " + numTests;
        assert(costOfTest != null) : "Init error: costOfTest cannot be null";
        assert(costOfTest.length == numTests) : "Init error: costOfTest length differ from numTests" + costOfTest.length + " vs. " + numTests;
        assert(A != null) : "Init error: A cannot be null";
        assert(A.length == numTests) : "Init error: Number of rows in A differ from numTests" + A.length + " vs. " + numTests;
        assert(A[0].length == numDiseases) : "Init error: Number of columns in A differ from numDiseases" + A[0].length + " vs. " + numDiseases;

        this.numTests = numTests;
        this.numDiseases = numDiseases;
        this.costOfTest = new double[numTests];
        for(int i=0; i < numTests; i++)
            this.costOfTest[i] = costOfTest[i];
        this.A = new int[numTests][numDiseases];
        for(int i=0; i < numTests; i++)
            for(int j=0; j < numDiseases; j++)
            this.A[i][j] = A[i][j];
        try {
            this.solution = this.solve();
        } catch (IloException e) {
            e.printStackTrace();
        }
        this.objValue = getObjectiveValue(this.solution);
        System.out.println("Solution: ");
        for (int i = 0; i < numTests; i++) {
            System.out.print(this.solution[i] + " ");
        }
        System.out.println();
        try {
            this.constraintMap = preComputeConstraints();
        } catch (IloException e) {
            e.printStackTrace();
        }
        // this.intObjValue = (int)Math.round(branchAndBoundBest());
        // System.out.println("Objective value: " + this.intObjValue);
        }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Number of tests: " + numTests + "\n");
        buf.append("Number of diseases: " + numDiseases + "\n");
        buf.append("Cost of tests: " + Arrays.toString(costOfTest) + "\n");
        buf.append("A:\n");
        for(int i=0; i < numTests; i++)
            buf.append(Arrays.toString(A[i]) + "\n");
        return buf.toString();
    }

    public int[][] createDifferenceMatrix() {
        int[][] differenceMatrix = new int[numTests][numDiseases * (numDiseases - 1) / 2];

        int colIndex = 0;
        for (int j = 0; j < numDiseases; j++) {
            for (int k = j + 1; k < numDiseases; k++) {
                for (int i = 0; i < numTests; i++) {
                    differenceMatrix[i][colIndex] = A[i][j] ^ A[i][k]; // XOR operation
                }
                colIndex++;
            }
        }

        return differenceMatrix;
    }

    // Function to solve the IP using CPLEX
    public double[] solve() throws IloException 
    {
        this.cplex = new IloCplex();
        this.cplex.setOut(null);

        // Decision variables
        this.isTestUsed = this.cplex.numVarArray(numTests, 0.0, 1.0);

        // IloIntVar[] x = new IloIntVar[numTests];

        // for (int i = 0; i < numTests; i++) {
        //     x[i] = this.cplex.intVar(0, 1, "x" + i); // Integer variable bounded between 0 and 1
        // }

        // Objective function
        IloLinearNumExpr objective = this.cplex.linearNumExpr();
        for (int i = 0; i < numTests; i++) {
            objective.addTerm(costOfTest[i], isTestUsed[i]);
        }
        cplex.addMinimize(objective);

        // Differentiation constraints
        int[][] differenceMatrix = createDifferenceMatrix();
        for (int j = 0; j < differenceMatrix[0].length; j++) {
            IloLinearNumExpr constraint = this.cplex.linearNumExpr();
            for (int i = 0; i < numTests; i++) {
                constraint.addTerm(differenceMatrix[i][j], isTestUsed[i]);
            }
            this.cplex.addGe(constraint, 1);
        }

        // Solve the model
        this.cplex.solve();

        // Extract solution
        double[] solution = this.cplex.getValues(isTestUsed);
        return solution;
    }


    // Function to get the index of the greatest fractional value
    public int greatestFractionalIdx(double[] arr) {
        double maxFractional = 0;
        int maxIdx = -1;
        for (int i = 0; i < arr.length; i++) {
            double fractionalPart = Math.abs(arr[i] - (int) arr[i]);
            if (fractionalPart > maxFractional) {
                maxFractional = fractionalPart;
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public int distanceFromHalf(double[] arr) {
        double minDistance = 1;
        int maxIdx = -1;
        for (int i = 0; i < arr.length; i++) {
            double curDist = Math.abs(arr[i] - 0.5);
            if (curDist < minDistance) {
                minDistance = curDist;
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    // Function to calculate the objective value
    public double getObjectiveValue(double[] curSol) {
        double objValue = 0;
        for (int i = 0; i < numTests; i++) {
            objValue += curSol[i] * costOfTest[i];
        }
        return objValue;
    }

    public double getRoundObjectiveValue(double[] curSol) {
        double objValue = 0;
        for (int i = 0; i < numTests; i++) {
            if (curSol[i] > 0) {
                objValue += costOfTest[i];
            }
        }
        return objValue;
    }

    private Map<String, IloConstraint[]> preComputeConstraints() throws IloException {
        Map<String, IloConstraint[]> constraintMap = new HashMap<>();

        for (int i = 0; i < numTests; i++) {
            IloConstraint[] constraints = new IloConstraint[2];
            constraints[0] = this.cplex.le(this.isTestUsed[i], 0);
            constraints[1] = this.cplex.ge(this.isTestUsed[i], 1);
            constraintMap.put("x_" + i, constraints);
        }

        return constraintMap;
    }
    
    public double[] applyConstraints(Map<Integer, Integer> constraints) throws IloException {
        List<IloConstraint> docplexConstraints = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : constraints.entrySet()) {
            int key = entry.getKey();
            int val = entry.getValue();
            docplexConstraints.add(this.constraintMap.get("x_" + key)[val]);
        }
        for (IloConstraint constraint : docplexConstraints) {
            this.cplex.add(constraint);
        }

        this.cplex.solve();

        // double retVal = Double.POSITIVE_INFINITY;
        double[] newSol = new double[numTests];
        if (this.cplex.getStatus().equals(IloCplex.Status.Optimal)) {
            newSol = this.cplex.getValues(isTestUsed);
        }

        // Clear the constraints
        for (IloConstraint constraint : docplexConstraints) {
            this.cplex.remove(constraint);
        }

        // Return the solution and objective value
        // newSol and retVal
        return newSol;
    }

    public double branchAndBoundBest() {
        double[] bestSolution = new double[numTests];
        PriorityQueue<BnBNode> priorityQueue = new PriorityQueue<>();
        double upperBound = Double.POSITIVE_INFINITY;

        // Add initial node to priority queue
        priorityQueue.offer(new BnBNode(this.objValue, solution, new HashMap<>()));

        while (!priorityQueue.isEmpty()) {
            BnBNode currentNode = priorityQueue.poll();
            double newVal = currentNode.getValue();

            if (newVal > upperBound) {
                continue;
            }

            int branchIdx = greatestFractionalIdx(currentNode.getSolution());

            if (branchIdx == -1) {
                System.out.println("Integer solution found: " + newVal);
                if (newVal < upperBound) {
                    upperBound = newVal;
                    bestSolution = currentNode.getSolution();
                }
            } else {
                Map<Integer, Integer> constraints = currentNode.getConstraints();
                
                Map<Integer, Integer> newConstraints1 = new HashMap<>(constraints);
                newConstraints1.put(branchIdx, 1);
                double[] newSolution1 = {};
                try {
                    newSolution1 = applyConstraints(newConstraints1);
                } catch (IloException e) {
                    e.printStackTrace();
                }
                double newObjective1 = getObjectiveValue(newSolution1);
                double newRoundObjective1 = getRoundObjectiveValue(newSolution1);
                upperBound = Math.min(upperBound, newRoundObjective1);
                if (newObjective1 <= upperBound) {
                    priorityQueue.offer(new BnBNode(newObjective1, newSolution1, newConstraints1));
                }

                Map<Integer, Integer> newConstraints0 = new HashMap<>(constraints);
                newConstraints0.put(branchIdx, 0);
                double[] newSolution0 = {};
                try {
                    newSolution0 = applyConstraints(newConstraints0);
                } catch (IloException e) {
                    e.printStackTrace();
                }
                double newObjective0 = getObjectiveValue(newSolution0);
                double newRoundObjective0 = getRoundObjectiveValue(newSolution0);
                upperBound = Math.min(upperBound, newRoundObjective0);
                if (newObjective0 <= upperBound) {
                    priorityQueue.offer(new BnBNode(newObjective0, newSolution0, newConstraints0));
                }
            }
        }

        System.out.println("Best solution: ");
        for (int i = 0; i < numTests; i++) {
            System.out.print(bestSolution[i] + " ");
        }
        System.out.println();

        return upperBound;
    }

    @Override
    public IPInstance clone() {
        IPInstance clone = new IPInstance();
        clone.numTests = this.numTests;
        clone.numDiseases = this.numDiseases;
        clone.costOfTest = this.costOfTest.clone();
        clone.A = new int[numTests][numDiseases];
        for (int i = 0; i < numTests; i++) {
            clone.A[i] = this.A[i].clone();
        }
        try {
            clone.solution = clone.solve();
        } catch (IloException e) {
            e.printStackTrace();
        }
        clone.objValue = getObjectiveValue(clone.solution);
        // System.out.println("Solution: ");
        // for (int i = 0; i < numTests; i++) {
        //     System.out.print(this.solution[i] + " ");
        // }
        // System.out.println();
        try {
            clone.constraintMap = clone.preComputeConstraints();
        } catch (IloException e) {
            e.printStackTrace();
        }
        return clone;
    }

}