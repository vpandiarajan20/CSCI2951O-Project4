package solver.ip;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main
{
  public static final int NUM_THREADS = 6;  
  public static void main(String[] args)
  {
		if(args.length == 0)
		{
			System.out.println("Usage: java Main <file>");
			return;
		}
		
		String input = args[0];
		Path path = Paths.get(input);
		String filename = path.getFileName().toString();


		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
		
		Timer watch = new Timer();
		watch.start();
		
		IPInstance instance = DataParser.parseIPFile(input);
		BranchAndBoundParallel bnb = new BranchAndBoundParallel(instance.numTests, instance.objValue, instance.solution, instance.costOfTest, instance, executor);

        try {
            // Run the branch-and-bound algorithm in parallel
            instance.intObjValue = (int)Math.round(bnb.branchAndBoundBest());
            System.out.println("Upper bound: " + instance.intObjValue);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            // Shut down the executor to release resources
            executor.shutdown();
        }

		String result = "OPT";
		if (instance.intObjValue == Integer.MAX_VALUE)
			result = "INF";
		else if (instance.intObjValue == Integer.MIN_VALUE)
			result = "-INF";
    
		watch.stop();
		System.out.println("{\"Instance\": \"" + filename +
				"\", \"Time\": " + String.format("%.2f",watch.getTime()) +
				", \"Result\": " + instance.intObjValue +
				", \"Solution\": " + result + "}");
  }
}
