import java.awt.Color;

public class HWProc extends MPI_Proc {
	
	private static int[] taskNum = {1, -1};
	private static int[] energyBank = {0};
	private static final int energy = 10;
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
        		MPI_Recv(taskBuffer, 1, MPI_INT, MPI_ANY_SOURCE, 0, MPI_COMM_WORLD, status);
        		System.out.println("Worker " + (status.MPI_SOURCE - 4) + " completed task " + taskBuffer[0]);
        		if(taskBuffer[1] != -1) {
        			rootEnergy += taskBuffer[1];
        			if(rootEnergy == energy) {
        				System.out.println("Termination energy reached, root shutting down");
        				break;
        			}
        		}
        		
        	}
        }
        
        //Gen
        if(rank == 1) {
        	for(int i = 1; i <= tasks; i++) {
        		MPI_Recv(emptyMsg, 1, MPI_INT, 2, reqTag, MPI_COMM_WORLD, status); //Get request
            	MPI_Send(taskNum, 1, MPI_INT, 2, taskTag, MPI_COMM_WORLD); //Send task
            	taskNum[0] = i;
        	}
        	MPI_Recv(emptyMsg, 1, MPI_INT, 2, reqTag, MPI_COMM_WORLD, status);
        	taskNum[0] = -2; //-2 denotes an empty task
        	taskNum[1] = energy;
        	MPI_Send(taskNum, 2, MPI_INT, 2, taskTag, MPI_COMM_WORLD);
        	System.out.println("Generator sent energy of " + energy);
        }
        //Comms
        if(rank >= 2 && rank <= 4) {
        	while(true) {     		
        		if(taskBuffer[0] == -2) { //Do energy stuff with empty task
        			//Split/send to its comm 
        			MPI_Recv(emptyMsg, 1, MPI_INT, rank + 1, reqTag, MPI_COMM_WORLD, status);
        			MPI_Send(taskBuffer, 2, MPI_INT, rank + 1, taskTag, MPI_COMM_WORLD);
        			System.out.println("SENT SPLIT PT1");
        			//Split/send to its worker
        			MPI_Recv(emptyMsg, 1, MPI_INT, rank + 3, reqTag, MPI_COMM_WORLD, status);
        			MPI_Send(taskBuffer, 2, MPI_INT, rank + 3, taskTag, MPI_COMM_WORLD);
        			System.out.println("SENT SPLIT PT2");
        			break;
        		}
        		if(taskBuffer[0] == -1) { //if buffer empty send request to left neighbor
        			MPI_Send(emptyMsg, 1, MPI_INT, rank - 1, reqTag, MPI_COMM_WORLD);
        			MPI_Recv(taskBuffer, 2, MPI_INT, rank - 1, taskTag, MPI_COMM_WORLD, status);
        			System.out.println("COMM " + (rank - 1) + " received task " + taskBuffer[0] + " from " + (status.MPI_SOURCE == 1 ? "generator" : ("COMM " + (status.MPI_SOURCE - 1)))  + " energy : " + taskBuffer[1]);
        		}
        		else { //comm has a task, send to requesting neighbor
        			MPI_Recv(emptyMsg, 1, MPI_INT, MPI_ANY_SOURCE, reqTag, MPI_COMM_WORLD, status);
        			MPI_Send(taskBuffer, 2, MPI_INT, status.MPI_SOURCE, taskTag, MPI_COMM_WORLD);
        			System.out.println("COMM " + (rank - 1) + " sent task " + taskBuffer[0] + " to " + (status.MPI_SOURCE >= 2 && status.MPI_SOURCE <= 4 ? ("COMM " + (status.MPI_SOURCE - 1)) : ("Worker " + (status.MPI_SOURCE - 4)))  + " energy : " + taskBuffer[1]);
        			taskBuffer[0] = -1; //set task buffer to empty because we sent it

        		}
        		//System.out.println(buffer[0]);
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
        		taskBuffer[0] = -1;
        		if(taskBuffer[1] == -2) {
        			break;
        		}
        	}
        }
        MPI_Finalize();
    }
    
    //Generates sequence in which energy will be split throughout the network
    //seq[commIndex] is the amount of energy they will send
    private void genSplitSeq(int[] seq) {
    	
    }
}
