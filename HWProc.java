import java.awt.Color;
import java.util.Random;

public class HWProc extends MPI_Proc {
	
	private static int[] taskNum = {1, -1};
	private static int[] sequence = new int[6];
	private static long seed = 79823832;
	private static final int energy = 20;
	private final int reqTag = 1;
	private final int taskTag = 2;
	
	public HWProc(MPI_World world, int rank)
    {
        super(world, rank);
    }

    public void exec(int argc, String argv[]) throws InterruptedException
    {
    	//0 - root
    	//1 - task generator
    	//2-4 - comm
    	//5-8 - workers
    	
    	int rootEnergy = 0;
    	int tasks = 30;
        MPI_Init(argc, argv);
        MPI_Status status = new MPI_Status();
        int[] emptyMsg = { 0 };
        int[] taskBuffer = { -1, -1 }; //1st element = task number 2nd element = energy
        int rank = MPI_Comm_rank(MPI_COMM_WORLD);
        int size = MPI_Comm_size(MPI_COMM_WORLD);
        
        //Root
        if(rank == 0) {
        	while(true) {
        		MPI_Recv(taskBuffer, 2, MPI_INT, MPI_ANY_SOURCE, 0, MPI_COMM_WORLD, status);
        		
        		if(taskBuffer[1] != -1) {
        			rootEnergy += taskBuffer[1];
        			System.out.println("Worker " + (status.MPI_SOURCE - 4) + " sent energy value " + taskBuffer[1] + " total root energy is now " + rootEnergy);
        			if(rootEnergy == energy) {
        				System.out.println("Termination energy reached, root shutting down");
        				break;
        			}
        		}
        		else {
        			System.out.println("Worker " + (status.MPI_SOURCE - 4) + " completed task " + taskBuffer[0]);
        		}
        		
        	}
        }
        
        //Gen
        if(rank == 1) {
        	for(int i = 0; i <= tasks; i++) {
        		MPI_Recv(emptyMsg, 1, MPI_INT, 2, reqTag, MPI_COMM_WORLD, status); //Get request
            	MPI_Send(taskNum, 1, MPI_INT, 2, taskTag, MPI_COMM_WORLD); //Send task
            	taskNum[0] = (i + 1);
        	}
        	MPI_Recv(emptyMsg, 1, MPI_INT, 2, reqTag, MPI_COMM_WORLD, status);
        	taskNum[0] = -2; //-2 denotes an empty task
        	taskNum[1] = energy;
        	MPI_Send(taskNum, 2, MPI_INT, 2, taskTag, MPI_COMM_WORLD);
        	System.out.println("*** Generator sent energy of " + energy);
        }
        //Comms
        if(rank >= 2 && rank <= 4) {
        	while(true) {     		
        		if(taskBuffer[0] == -2) { //Do energy stuff with empty task
        			int offset = 0;
        			genSplitSeq();
        			//Split/send to its comm 
        			if(rank != 4) {
        				MPI_Recv(emptyMsg, 1, MPI_INT, rank + 1, reqTag, MPI_COMM_WORLD, status);
        				//Ghetto work around to map sequence array to rank
        				switch(rank) {
        				case 2: offset = -2;
        				break;
        				case 3: offset = -1;
        				break;
        				}
        				taskBuffer[1] = sequence[rank + offset];
        				MPI_Send(taskBuffer, 2, MPI_INT, rank + 1, taskTag, MPI_COMM_WORLD);
        				System.out.println("COMM " + (rank - 1) + " sent task EMPTY TASK to " + (status.MPI_SOURCE >= 2 && status.MPI_SOURCE <= 4 ? ("COMM " + (status.MPI_SOURCE - 1)) : ("Worker " + (status.MPI_SOURCE - 4))) + " with energy " + taskBuffer[1]);
        				offset = 0;
        			}
        			else { //rank 4 sends to both workers
        				MPI_Recv(emptyMsg, 1, MPI_INT, rank + 4, reqTag, MPI_COMM_WORLD, status);
        				taskBuffer[1] = sequence[rank];
        				MPI_Send(taskBuffer, 2, MPI_INT, rank + 4, taskTag, MPI_COMM_WORLD);
        				System.out.println("COMM " + (rank - 1) + " sent task EMPTY TASK to " + (status.MPI_SOURCE >= 2 && status.MPI_SOURCE <= 4 ? ("COMM " + (status.MPI_SOURCE - 1)) : ("Worker " + (status.MPI_SOURCE - 4))) + " with energy " + taskBuffer[1]);
        				offset = 0;
        			}
        			//Split/send to its worker
        			MPI_Recv(emptyMsg, 1, MPI_INT, rank + 3, reqTag, MPI_COMM_WORLD, status);
        			//Same street level code
        			switch(rank) {
        			case 2: offset = -1;
        			break;
        			case 4: offset = 1;
        			break;
        			}
        			taskBuffer[1] = sequence[rank + offset];
        			MPI_Send(taskBuffer, 2, MPI_INT, rank + 3, taskTag, MPI_COMM_WORLD);
        			System.out.println("COMM " + (rank - 1) + " sent task EMPTY TASK to " + (status.MPI_SOURCE >= 2 && status.MPI_SOURCE <= 4 ? ("COMM " + (status.MPI_SOURCE - 1)) : ("Worker " + (status.MPI_SOURCE - 4))) + (taskBuffer[1] == -1 ? "" : (" with energy " + taskBuffer[1])));
        			break;
        		}
        		if(taskBuffer[0] == -1) { //if buffer empty send request to left neighbor
        			MPI_Send(emptyMsg, 1, MPI_INT, rank - 1, reqTag, MPI_COMM_WORLD);
        			MPI_Recv(taskBuffer, 2, MPI_INT, rank - 1, taskTag, MPI_COMM_WORLD, status);
        			System.out.println("COMM " + (rank - 1) + " received task " + (taskBuffer[0] == -2 ? " EMPTY TASK " : taskBuffer[0]) + " from " + (status.MPI_SOURCE == 1 ? "generator" : ("COMM " + (status.MPI_SOURCE - 1))) + (taskBuffer[1] == -1 ? "" : (" with energy " + taskBuffer[1])));
        		}
        		else { //comm has a task, send to requesting neighbor
        			MPI_Recv(emptyMsg, 1, MPI_INT, MPI_ANY_SOURCE, reqTag, MPI_COMM_WORLD, status);
        			MPI_Send(taskBuffer, 2, MPI_INT, status.MPI_SOURCE, taskTag, MPI_COMM_WORLD);
        			System.out.println("COMM " + (rank - 1) + " sent " + taskBuffer[0] + " to " + (status.MPI_SOURCE >= 2 && status.MPI_SOURCE <= 4 ? ("COMM " + (status.MPI_SOURCE - 1)) : ("Worker " + (status.MPI_SOURCE - 4))) + (taskBuffer[1] == -1 ? "" : (" with energy " + taskBuffer[1])));
        			taskBuffer[0] = -1; //set task buffer to empty because we sent it

        		}
        	}
        }
   
      
        //Workers
        if(rank >=5 && rank <= 8) {
        	//Request task from its comm
        	while(true) {
        		if(rank == 8 || rank == 7) {
        			MPI_Send(emptyMsg, 1, MPI_INT, 4, reqTag, MPI_COMM_WORLD);
        			MPI_Recv(taskBuffer, 2, MPI_INT, 4, taskTag, MPI_COMM_WORLD, status);
        		}
        		else {
        			MPI_Send(emptyMsg, 1, MPI_INT, (rank - 3), reqTag, MPI_COMM_WORLD);
        			MPI_Recv(taskBuffer, 2, MPI_INT, (rank - 3), taskTag, MPI_COMM_WORLD, status);
        		}
        		Thread.sleep(2000);
        		MPI_Send(taskBuffer, 2, MPI_INT, 0, 0, MPI_COMM_WORLD);
        		if(taskBuffer[0] == -2) {
        			break;
        		}
           		taskBuffer[0] = -1;
        	}
        }
        MPI_Finalize();
    }
    
    //Generates sequence in which energy will be split throughout the network
    //seq[commIndex] is the amount of energy they will send
    //e.g. sequence[0] is the value sent to comm2 and sequence[1] is the value sent to worker 1
    private void genSplitSeq() {
    	//Randomly determine splitting path
    	Random r = new Random(seed);
    	int e = energy;
    	for(int i = 0; i < sequence.length; i += 2) {
    		sequence[i] = (e == 0 ? 0 : (r.nextInt(e) + 1));
    		sequence[i+1] = (e - sequence[i]);
    		e = sequence[i];
    	}
    }
}
