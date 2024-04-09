package solver.ip;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.util.concurrent.locks.*;
import ilog.cplex.*;
import ilog.concert.*;

public class BranchAndBoundParallel {
    private final int numTests;
    private final double objValue;
    private final double[] solution;
    private final ExecutorService executor;
    private final AtomicLong upperBound;
    // private Lock lock = new ReentrantLock();
    private double[] costOfTest;
    private IPInstance IPInstance;
    private final AtomicLong numThreadsInProgress;


    public BranchAndBoundParallel(int numTests, double objValue, double[] solution, double[] costOfTest, IPInstance IPInstance, ExecutorService executor) {
        this.numTests = numTests;
        this.objValue = objValue;
        this.solution = solution;
        this.executor = executor;
        this.costOfTest = costOfTest;
        this.IPInstance = IPInstance;
        this.upperBound = new AtomicLong(Long.MAX_VALUE);
        this.numThreadsInProgress = new AtomicLong(0);
    }

    public double branchAndBoundBest() throws InterruptedException, ExecutionException {

        // Create a concurrent priority queue
        PriorityBlockingQueue<BnBNode> priorityQueue = new PriorityBlockingQueue<>();

        // Add initial node to priority queue
        priorityQueue.offer(new BnBNode(this.objValue, solution, new HashMap<>()));

        // Define tasks for popping elements off the priority queue
        List<Callable<Void>> popTasks = new ArrayList<>();
        for (int i = 0; i < Main.NUM_THREADS; i++) {
            popTasks.add(() -> {
                IPInstance my_ipInstance = this.IPInstance.clone();
                BnBNode currentNode;
                // while (!priorityQueue.isEmpty() || this.numThreadsInProgress.get() > 0) {
                do {
                    // BnBNode currentNode = priorityQueue.poll(500, TimeUnit.MILLISECONDS);
                    currentNode = priorityQueue.poll(500, TimeUnit.MILLISECONDS);
                    // BnBNode currentNode = priorityQueue.poll();
                    if (currentNode == null) {
                        System.out.println("Thread " + Thread.currentThread().getId() + " ran into null node");
                        break;
                    }
                    // System.out.println("Thread " + Thread.currentThread().getId() + " popped node with value: " + currentNode.getValue() + " and constraints: " + currentNode.getConstraints() + "pq size: " + priorityQueue.size());
                    // this.numThreadsInProgress.incrementAndGet();
                    double newVal = currentNode.getValue();

                    if (newVal > this.upperBound.get()) {
                        // this.numThreadsInProgress.decrementAndGet();
                        continue;
                    }
                    // int branchIdx = IPInstance.greatestFractionalIdx(currentNode.getSolution());
                    int branchIdx = IPInstance.distanceFromHalf(currentNode.getSolution());

                    if (branchIdx == -1) {
                        // System.out.println("Integer solution found: " + newVal);
                        if (newVal < this.upperBound.get()) {
                            this.upperBound.set((long) newVal);
                            // bestSolution = currentNode.getSolution();
                        }
                    } else {
                        Map<Integer, Integer> constraints = currentNode.getConstraints();
                        
                        Map<Integer, Integer> newConstraints1 = new HashMap<>(constraints);
                        newConstraints1.put(branchIdx, 1);
                        double[] newSolution1 = {};
                        try {
                            newSolution1 = my_ipInstance.applyConstraints(newConstraints1);
                        } catch (IloException e) {
                            e.printStackTrace();
                        }
                        double newObjective1 = getObjectiveValue(newSolution1);
                        double newRoundObjective1 = getRoundObjectiveValue(newSolution1);
                        if (newRoundObjective1 < this.upperBound.get()){
                            this.upperBound.set((long) newRoundObjective1);
                        }
                        if (newObjective1 <= this.upperBound.get()) {
                            priorityQueue.offer(new BnBNode(newObjective1, newSolution1, newConstraints1));
                        }

                        Map<Integer, Integer> newConstraints0 = new HashMap<>(constraints);
                        newConstraints0.put(branchIdx, 0);
                        double[] newSolution0 = {};
                        try {
                            newSolution0 = my_ipInstance.applyConstraints(newConstraints0);
                        } catch (IloException e) {
                            e.printStackTrace();
                        }
                        double newObjective0 = getObjectiveValue(newSolution0);
                        double newRoundObjective0 = getRoundObjectiveValue(newSolution0);
                        if (newRoundObjective0 < this.upperBound.get()){
                            this.upperBound.set((long) newRoundObjective0);
                        }
                        if (newObjective0 <= this.upperBound.get()) {
                            priorityQueue.offer(new BnBNode(newObjective0, newSolution0, newConstraints0));
                        }
                    }
                    // this.numThreadsInProgress.decrementAndGet();
                } while (true);
                System.out.println("Thread " + Thread.currentThread().getId() + " finished");
                return null;
            });
        }

        // Execute pop tasks concurrently using ExecutorService
        executor.invokeAll(popTasks);

        return upperBound.get();
    }

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
}
