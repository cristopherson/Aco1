import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
/*
 *  === Implementation of ant swarm TSP solver. ===
 *  
 * The algorithm is described in [1, page 8].
 * 
 * == Tweaks/notes == 
 *  - I added a system where the ant chooses with probability
 *    "pr" to go to a purely random town. This did not yield better
 * results so I left "pr" fairly low.
 *  - Used an approximate pow function - the speedup is
 *    more than a factor of 10! And accuracy is not needed
 *    See AntTsp.pow for details.
 *  
 * == Parameters ==
 * I set the parameters to values suggested in [1]. My own experimentation
 * showed that they are pretty good.
 * 
 * == Usage ==
 * - Compile: javac AntTsp.java
 * - Run: java AntTsp <TSP file>
 * 
 * == TSP file format ==
 * Full adjacency matrix. Columns separated by spaces, rows by newline.
 * Weights parsed as doubles, must be >= 0.
 * 
 * == References == 
 * [1] M. Dorigo, The Ant System: Optimization by a colony of cooperating agents
 * ftp://iridia.ulb.ac.be/pub/mdorigo/journals/IJ.10-SMC96.pdf
 * 
 */

public class AntTsp {
    // Algorithm parameters:
    // original amount of trail
    private double c = 1.0;
    // trail preference
    private double alpha = 1;
    // greedy preference
    private double beta = 5;
    // trail evaporation coefficient
    private double evaporation = 0.5;
    // new trail deposit coefficient;
    private double Q = 500;
    // number of ants used = numAntFactor*numTowns
    private double numAntFactor = 0.8;
    // probability of pure random selection of the next town
    private double pr = 0.3;

    private int startTown = 0;

	private int goalTown = 1;
	// Reasonable number of iterations
    private int maxIterations = 80000;

    public int n = 0; // # towns
    public int m = 0; // # ants
    private double graph[][] = null;

	private double trails[][] = null;
    private Ant ants[] = null;
    private Random rand = new Random();
    private double probs[] = null;

	private int currentIndex = 0;

    public int[] bestTour;
	public double bestTourLength;

    /* There are 4 supported directions, 0:LEFT, 1:UP, 2:RIGHT, 3:DOWN*/
    private List <Integer>directions = new ArrayList<Integer>();

    // Ant class. Maintains tour and tabu information.
    private class Ant {
        public int tour[] = new int[graph.length];
        // Maintain visited list for towns, much faster
        // than checking if in tour so far.
        public boolean visited[] = new boolean[graph.length];
        private boolean completed = false;
        private boolean dead = false;

        public Ant() {
        	for(int i = 0; i< n; i++)
        		tour[i] = -1;
        }
        public int getNextTown(){
        	return tour[currentIndex + 1];        	
        }
        
	    public boolean isDead() {
			return dead;
		}
	
		public void setDead(boolean dead) {
			this.dead = dead;
		}

	    public boolean isCompleted() {
			return completed;
		}
	
		public void setCompleted(boolean completed) {
			this.completed = completed;
		}

		private int getNeighbor(int direction) {
			int neighbor = -1;
			switch(direction) {
			case 0:
			{
				if((tour[currentIndex] % 10) != 0) {
					neighbor = tour[currentIndex] - 1;
				}
				break;
			}
			case 1:
			{
				if(tour[currentIndex] >= 10) {
					neighbor = tour[currentIndex] - 10;
				}
				break;
			}
			case 2:
			{
				if(((tour[currentIndex] + 1) % 10) != 0) {
					neighbor = tour[currentIndex] + 1;
				}
				break;
			}
			case 3:
			{
				if(tour[currentIndex] < 90) {
					neighbor = tour[currentIndex] + 10;
				}
				break;
			}
			default:
				neighbor = -1;
			}
			return neighbor;
	    }

        public void visitTown(int town) {
            tour[currentIndex + 1] = town;
            visited[town] = true;
        }

        public boolean visited(int i) {
            return visited[i];
        }

        public double tourLength() {
            double length = 1;
            for (int i = 0; i < n - 1; i++) {
            	if(tour[i] != -1 && tour[i + 1] != -1)
            		length += graph[tour[i]][tour[i + 1]];
            	else
            		break;
            }
            return length;
        }

    	public void clear() {
            for (int i = 0; i < n; i++) {
//           		tour[i] = -1;
                visited[i] = false;
//                completed = false;
            }
        }
    }

    
    public AntTsp() {
    	for (int i = 0; i < 4; i++)
    		directions.add(new Integer(i));
    	
    }
    // Read in graph from a file.
    // Allocates all memory.
    // Adds 1 to edge lengths to ensure no zero length edges.
    public void readGraph(String path) throws IOException {
        FileReader fr = new FileReader(path);
        BufferedReader buf = new BufferedReader(fr);
        String line;
        int i = 0;

        while ((line = buf.readLine()) != null) {
            String splitA[] = line.split(" ");
            LinkedList<String> split = new LinkedList<String>();
            for (String s : splitA)
                if (!s.isEmpty())
                    split.add(s);

            if (graph == null)
                graph = new double[split.size()][split.size()];
            int j = 0;

            for (String s : split)
                if (!s.isEmpty())
                    graph[i][j++] = Double.parseDouble(s) + 1;

            i++;
        }

        n = graph.length;
        m = (int) (n * numAntFactor);

        // all memory allocations done here
        trails = new double[n][n];
        probs = new double[n];
        ants = new Ant[m];
        for (int j = 0; j < m; j++)
            ants[j] = new Ant();
    }

    public int[] getBestTour() {
		return bestTour;
	}

    // Approximate power function, Math.pow is quite slow and we don't need accuracy.
    // See: 
    // http://martin.ankerl.com/2007/10/04/optimized-pow-approximation-for-java-and-c-c/
    // Important facts:
    // - >25 times faster
    // - Extreme cases can lead to error of 25% - but usually less.
    // - Does not harm results -- not surprising for a stochastic algorithm.
    public static double pow(final double a, final double b) {
        final int x = (int) (Double.doubleToLongBits(a) >> 32);
        final int y = (int) (b * (x - 1072632447) + 1072632447);
        return Double.longBitsToDouble(((long) y) << 32);
    }

    // Store in probs array the probability of moving to each town
    // [1] describes how these are calculated.
    // In short: ants like to follow stronger and shorter trails more.
    private void probTo(Ant ant) {
        int i = ant.tour[currentIndex];

        double denom = 0.0;
        for (int l = 0; l < directions.size(); l++) {
        	if(ant.getNeighbor(l) < 0)
        		continue;
            if (!ant.visited(ant.getNeighbor(l)))
                denom += pow(trails[i][ant.getNeighbor(l)], alpha)
                        * pow(1.0 / graph[i][ant.getNeighbor(l)], beta);
        }

        if(denom == 0.0)
        	return;

        for (int j = 0; j < directions.size(); j++) {
        	if(ant.getNeighbor(j) < 0)
        		continue;
            if (ant.visited(ant.getNeighbor(j))) {
                probs[ant.getNeighbor(j)] = 0.0;
            } else {
                double numerator = pow(trails[i][ant.getNeighbor(j)], alpha)
                        * pow(1.0 / graph[i][ant.getNeighbor(j)], beta);
                probs[ant.getNeighbor(j)] = numerator / denom;
            }
        }

    }

    // Given an ant select the next town based on the probabilities
    // we assign to each town. With pr probability chooses
    // totally randomly (taking into account tabu list).
    private int selectNextTown(Ant ant) {
        // sometimes just randomly select
//    	Collections.shuffle(directions);
    	
        if (rand.nextDouble() < pr) {
            int t = rand.nextInt(directions.size()); // random town
            int j = -1;
            for (int i = 0; i < directions.size(); i++) {
            	int selectedTown = ant.getNeighbor(directions.get(i));
            	if(selectedTown < 0) {
            		continue;
            	}
            	
                if (!ant.visited(selectedTown))
                    j++;
                if (j == t)
                    return ant.getNeighbor(directions.get(i));
            }
            if(j < 0)
            	return -1;
        }
        // calculate probabilities for each town (stored in probs)
        probTo(ant);
        // randomly select according to probs
        double r = rand.nextDouble();
        double tot = 0;
        for (int i = 0; i < directions.size(); i++) {
        	if(ant.getNeighbor(i) < 0)
        		continue;
        	
            tot += probs[ant.getNeighbor(i)];
            if (tot >= r)
                return ant.getNeighbor(i);
        }

        for (int i = 0; i < directions.size(); i++) {
        	int selectedTown = ant.getNeighbor(directions.get(i));
        	if(selectedTown < 0) {
        		continue;
        	}
        	
            if (!ant.visited(selectedTown))
                return ant.getNeighbor(directions.get(i));
        }
        
        return -1;
    }

    // Update trails based on ants tours
    private void updateTrails() {
        // evaporation
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                trails[i][j] *= evaporation;

        // each ants contribution
        for (Ant a : ants) {
            double contribution = Q / a.tourLength();
            for (int i = 0; i < n - 1; i++) {
            	if(a.tour[i] != -1 && a.tour[i + 1] != -1)
            		trails[a.tour[i]][a.tour[i + 1]] += contribution;
            	else
            		break;
            }
//            trails[a.tour[n - 1]][a.tour[0]] += contribution;
        }
    }

    // Choose the next town for all ants
    private void moveAnts() {
        // each ant follows trails...
        while (currentIndex < n - 1) {
            for (Ant a : ants) {
            	if(!a.isCompleted() && !a.isDead()) {
            		int selectedTown =selectNextTown(a);
            		
            		if(selectedTown < 0) {
            			a.setDead(true);
            			int validNodesCounter = 0;
            			for(int i = 0; i < a.tour.length -1 ; i++) {
            				if(a.tour[i] == -1 || a.tour[i + 1] == -1) {
            					break;
            				}
            				validNodesCounter++;
            			}

            			for(int i = 0; i < validNodesCounter -1 ; i++) {
            				if(a.tour[i] == -1 || a.tour[i + 1] == -1) {
            					break;
            				}
            				trails[a.tour[i]][a.tour[i + 1]] = c;
            			}
            			a.setCompleted(true);
            			System.out.println("Ant " + a + " is dead");
            			continue;
            		}
	                a.visitTown(selectedTown);
	                if(a.getNextTown() == goalTown) {
	                	a.setCompleted(true);
	                }
            	}
            }
            currentIndex++;
        }
    }

    // m ants with random start city
    private void setupAnts() {
        currentIndex = -1;
        for (int i = 0; i < m; i++) {
            ants[i].clear(); // faster than fresh allocations.
            ants[i].visitTown(startTown);
        }
        currentIndex++;

    }

    private void updateBest() {
        if (bestTour == null) {
            bestTour = ants[0].tour;
            bestTourLength = ants[0].tourLength();
        }
        for (Ant a : ants) {
            if (a.tourLength() < bestTourLength) {
                bestTourLength = a.tourLength();
                bestTour = a.tour.clone();
            }
        }
    }

    public static String tourToString(int tour[]) {
        String t = new String();
        for (int i : tour)
            t = t + " " + i;
        return t;
    }

    public int[] solve() {
        // clear trails
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                trails[i][j] = c;

        int iteration = 0;
        // run for maxIterations
        // preserve best tour
        while (iteration < maxIterations) {
            setupAnts();
            moveAnts();
            updateTrails();
            updateBest();
            iteration++;
        }
        // Subtract n because we added one to edges on load
        System.out.println("Best tour length: " + (bestTourLength - n));
        System.out.println("Best tour:" + tourToString(bestTour));
        return bestTour.clone();
    }
    
    public double[][] getGraph() {
		return graph;
	}

	public void setGraph(double[][] graph) {
		this.graph = graph;
	}

    public int getGoalTown() {
		return goalTown;
	}

	public void setGoalTown(int goalTown) {
		this.goalTown = goalTown;
	}

    public int getStartTown() {
		return startTown;
	}

	public void setStartTown(int startTown) {
		this.startTown = startTown;
	}
    // Load graph file given on args[0].
    // (Full adjacency matrix. Columns separated by spaces, rows by newlines.)
    // Solve the TSP repeatedly for maxIterations
    // printing best tour so far each time. 
    public static void main(String[] args) {
        // Load in TSP data file.
        /*if (args.length < 1) {
            System.err.println("Please specify a TSP data file.");
            return;
        }*/
        AntTsp anttsp = new AntTsp();
        try {
//            anttsp.readGraph(args[0]);
            anttsp.readGraph("/home/cristopherson/workspace/Aco1/bin/tspadata3.txt");
        } catch (IOException e) {
            System.err.println("Error reading graph.");
            return;
        }

        anttsp.setStartTown(55);
        anttsp.setGoalTown(22);
        // Repeatedly solve - will keep the best tour found.
//        for (; ; ) {
            anttsp.solve();
//        }

    }
}
