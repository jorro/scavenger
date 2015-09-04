package scavenger.demo.clustering;

import scavenger.demo.clustering.distance.*;
import scavenger.demo.clustering.errorCalculation.*;
import scavenger.demo.clustering.enums.*;
import scavenger.*;
import scavenger.app.ScavengerAppJ;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Date;
import java.util.Calendar;

import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import scala.concurrent.Future;

import akka.dispatch.Futures;
import akka.util.Timeout;
import static akka.dispatch.Futures.future;
import static akka.dispatch.Futures.sequence;
import akka.dispatch.*;


import java.io.BufferedReader;
import java.io.InputStreamReader;
/**
 * Performs Diana (DIvisive ANAlysis) clustering using scavenger
 * Attempts to remove the issue of outliers by trying n possible nodes as the node which starts the splinter cluster.
 *
 */
public class Diana<T> extends ScavengerAppJ // implements Runnable
{
    private DianaDistanceFunctions dianaDistanceFunctions;    
    private DistanceMeasureSelection[] dataInfo;
    private int runTimeSeconds = 0;
    private int numberOfStartSplinterNodes = 0;
    
    private DiameterMeasure diameterMeasure = null;
    private List<Integer> trimmedMeanPercent = new ArrayList<Integer>();
    
    private ErrorCalculation<T> errorCalculation = null; // Are the clusters "good" clusters?
    private TreeNode<T> bestResult; 
    private double smallestError = Double.MAX_VALUE;
    private boolean isClustered = false;
    
    private double errorThreshold = 0.0;//0.04
    
    private int timeoutSeconds = 60;//timeout for a single job //TODO set in ChemicalClustering
    
    private int numberOfSplinters = 0;
    
    private ResultHandler<T> resultHandler = null;
    
    private int numJobs = 0;
    
    /////// Constructors ///////
    
    /**
     * 
     * @param distanceMeasure The distance measure to be used on all data items.
     */
    public Diana(DistanceMeasure distanceMeasure)
    {
        super();
        DistanceMeasureSelection dataInfo = new DistanceMeasureSelection("auto", distanceMeasure, 1);
        this.dataInfo = new DistanceMeasureSelection[]{dataInfo};
    }
    
    /**
     * @param distanceMeasure The distance measure (with weighting) used on all data items.
     */
    public Diana(DistanceMeasureSelection distanceMeasureSelection)
    {
        super();
        this.dataInfo = new DistanceMeasureSelection[]{distanceMeasureSelection};
    }
    
    /**
     * @param distanceMeasureSelection A list of the different distance measures with weightings. (@see DistanceMeasureSelection)
     */
    public Diana(List<DistanceMeasureSelection> distanceMeasureSelection)
    {
        super();
        this.dataInfo = distanceMeasureSelection.toArray(new DistanceMeasureSelection[distanceMeasureSelection.size()]);
    }
    
    ///////////////////////////
    
    ////// setters ///////////
    
    public void setErrorCalculation(ErrorCalculation<T> errorCalculation)
    {
        this.errorCalculation = errorCalculation;
    }
    
    public void setErrorThreshold(double errorThreshold)
    {
        this.errorThreshold = errorThreshold;
    }
    
    public void setRunTimeSeconds(int runTimeSeconds)
    {
        this.runTimeSeconds = runTimeSeconds;
    }
    
    public void setDiameterMeasure(DiameterMeasure diameterMeasure)
    {
        this.diameterMeasure = diameterMeasure;
    }
    
    public void setNumberOfStartSplinterNodes(int numberOfStartSplinterNodes)
    {
        this.numberOfStartSplinterNodes = numberOfStartSplinterNodes;
    }
    
    public void setNumberOfSplinters(int numberOfSplinters)
    {
        this.numberOfSplinters = numberOfSplinters;
    }
    
    public void setTimeoutSeconds(int timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public void setResultHandler(ResultHandler<T> resultHandler)
    {
        this.resultHandler = resultHandler;
    }
    
    public void setTrimmedMeanPercent(List<Integer> trimmedMeanPercent)
    {
        this.trimmedMeanPercent = trimmedMeanPercent;
    }
    
    public void setTrimmedMeanPercent(int trimmedMeanPercent)
    {
        this.trimmedMeanPercent = new ArrayList<Integer>();
        this.trimmedMeanPercent.add(trimmedMeanPercent);
    }
    ///////////////////////////
    
    private void setDefaults()
    {
        System.out.println("Diana : setting defaults ");
        if (errorCalculation == null)
        {
            System.out.println("ErrorCalculation has not been set using SimpleErrorCalculation with errorThreshold of " + errorThreshold);
            this.errorCalculation = new SimpleErrorCalculation(errorThreshold);
        }
        if (runTimeSeconds == 0)
        {
            System.out.println("runTimeSeconds has not been set using default (30)");
            runTimeSeconds = 30; 
        }
        if (numberOfStartSplinterNodes == 0)
        {
            System.out.println("numberOfStartSplinterNodes has not been set using default (3)");
            numberOfStartSplinterNodes = 3; 
        }
        if (diameterMeasure == null)
        {
            System.out.println("diameterMeasure has not been set using default (DiameterMeasure.TRIMMED_MEAN)");
            diameterMeasure = DiameterMeasure.TRIMMED_MEAN;//LARGEST_AVERAGE_DISTANCE; 
        }
        if((trimmedMeanPercent.size() == 0) && (diameterMeasure == DiameterMeasure.TRIMMED_MEAN))
        {
            System.out.println("trimmedMeanPercent has not been set using default (5)");
            trimmedMeanPercent.add(5);
        }
    }
    
    /**
     *
     * @param root The root TreeNode that contains all the data to be clusted.
     * @param numberOfIterations The number of times the cluster should be split up 
     *
     * @return The root for the tree of clusters.
     */
    public TreeNode<T> runClustering(TreeNode<T> root) 
    {    
        if(root.getData().size() <= 1)
        {
            System.out.println("Warning : <=1 items given");
            return root;
        }
        System.out.println("Diana.runClustering() called");
        System.out.println("Help : ");
        System.out.println("    p : Prints current result");
        System.out.println("    q : Stops clustering and returns current result \n");
        
        this.setDefaults();
        
        startScavenger();
        dianaDistanceFunctions = new DianaDistanceFunctions(dataInfo, numberOfStartSplinterNodes, diameterMeasure);  
        dianaDistanceFunctions.setTrimmedMeanPercent(trimmedMeanPercent);
        //dianaDistanceFunctions.setScavengerContext(scavengerContext());   
                   
        
        PriorityQueue<TreeNode<T>> results = new PriorityQueue<TreeNode<T>>(1, new TreeNodeComparator<T>());
        numJobs = 0;
        smallestError = Double.MAX_VALUE;
        isClustered = false;
        
        root.setToBeSplitOn(dianaDistanceFunctions.getIndexFurthestPoints(root));
        results.add(root);
        
        System.out.println("Diana : running clustering");
        
        // For all results 
        //      For all nodes the new splinter cluster can be started on 
        //          create a Future which performs the splitting of the cluster
        //
        // The TreeNode returned, from the future, is the next node to be splintered (the node with the largest diameter)         
        List<Future<TreeNode<T>>> futures = new ArrayList<Future<TreeNode<T>>>();  
        Calendar calendar = Calendar.getInstance(); // gets a calendar using the default time zone and locale.
        calendar.add(Calendar.SECOND, runTimeSeconds);
        Date endTime = calendar.getTime();
        while (!isClustered && endTime.after(new Date()) && ((results.size() != 0) || (numJobs != 0)))
        {      
            if(results.size() > 0)  
            {
                TreeNode<T> result = results.poll();                
                for(int i = 0; i < result.getToBeSplitOn().size(); i++)
                {
                    numJobs = numJobs + 1;
                    ScavengerFunction<TreeNode<T>> run = new CreateNewSplinter(result.getToBeSplitOn().get(i), dianaDistanceFunctions, numberOfSplinters);
                    Algorithm<TreeNode<T>, TreeNode<T>> algorithm = scavengerAlgorithm.expensive("createNewSplinter", run);
                    Computation<TreeNode<T>> computation = scavengerComputation.apply("node_"+result+result.getToBeSplitOn().get(i), result);                    
                    
                    Computation<TreeNode<T>> applyComputation = algorithm.apply(computation).cacheGlobally();
                    Future future = scavengerContext().submit(applyComputation);
                    
                    future.onSuccess(new OnSuccess<TreeNode<T>>() 
                                     {
                                         public void onSuccess(TreeNode<T> currentResult) 
                                         {
                                             
                                             setIsClustered(currentResult); 
                                             results.add(currentResult);
                                             decrementNumberOfJobs();
                                         }
                                     }, scavengerContext().executionContext());
                }
            }              
            this.handleKeyboardInput(); 
        }
        
        System.out.println("Finished");
        System.out.println("Smallest error : " + smallestError);
        return bestResult.getRoot();
    }
    
    public void decrementNumberOfJobs()
    {
        numJobs = numJobs - 1;
    }
    
    /**
     * Allows user to ask for results and for the clustering to finish
     */
    public void handleKeyboardInput()
    {
        try 
        {
            Thread.sleep(80); // TODO try shorter sleep
            //Thread.yield();
            if(System.in.available() > 0)
            {
                int keyboardInput = System.in.read();
                if ((keyboardInput == (int)'p') && (resultHandler != null))
                {
                    resultHandler.handleResults(bestResult.getRoot());
                }
                else if (keyboardInput == (int)'q')
                {
                    isClustered = true;
                }
            }
        }
        catch(Exception e) 
        { 
            e.printStackTrace(); 
        }
    }
    
       
    /**
     * Checks if the clustering has been completed
     *
     * @param results The list returned by the list of futures.
     */
    private void setIsClustered(TreeNode<T> result)//List<TreeNode<T>> results)
    {        
        TreeNode<T> root = result.getRoot();//dianaDistanceFunctions.findRoot(result);
        List<TreeNode<T>> leaves = root.findLeafNodes();
        
        isClustered = errorCalculation.isClustered(leaves, dianaDistanceFunctions);
        if (errorCalculation.getLastError() <= smallestError)
        {
            smallestError = errorCalculation.getLastError();
            bestResult = result;
        }
        result.setError(errorCalculation.getLastError());
        
    }
    
    /**
     *
     */
    public void endClustering()
    {
        scavengerShutdown();  
    }
}


/**
     * Checks if the cluster has been completed
     */
   /* public boolean isClustered(TreeNode<T> result)
    {
        return errorCalculation.isClustered(dianaDistanceFunctions.findLeafNodes(result), dianaDistanceFunctions);
    }*/


/* if (result.getChildLeft() != null)// is not the first time
            {
               
                List<TreeNode<T>> nodes = getNodeListWithoutLeafNodes(result); // the nodes at which a descision has been made
                                                                              // the nodes as the start of the list should be closest to the bottom of the tree
                root = null;
                for (TreeNode<T> node : nodes) // find a node that we have not tried all descisions for
                {
                    if (node.getToBeSplitOn().peek() != null) 
                    {
                        System.out.println("runClustering node.getToBeSplitOn() : " + node.getToBeSplitOn());
                        root = node;
                        break;
                    }
                }
                
                if (root == null) 
                {
                    System.out.println("Failed to fall below error. Returning last results");
                    return result;
                }
                root.removeHigherSplitNodes(findRoot(root));
            }*/
/* public Diana getOuter() 
    {
        return this;
    }*/
    
    /*
     * Finds object with highest average distance. This object becomes the start of the new cluster (leftLeaf).
     * If an item is closure to the new cluster (leftLeaf), than the old cluster (rightLeaf) :
     *       remove it from the old cluster (rightLeaf), and add it to the new cluster (leftLeaf).
     *
     * @param parent The TreeNode containing cluster to be split up
     * @return The parent TreeNode with it's children set to the TreeNodes containing the new clusters.
     */
   /* class CreateNewSplinter extends ScavengerFunction<TreeNode<T>> 
    {
        private int splinterStart;
        public CreateNewSplinter(int splinterStart)
        {
            this.splinterStart = splinterStart;
        }
        public TreeNode<T> call()
        { 
            scavengerContext = ctx;
            /*for (DistanceMeasureSelection temp : dataInfo)
            {
                temp.getDistanceMeasure().setScavengerContext(ctx);
            }*
            TreeNode parent = value;
            
            
            System.out.println("CreateNewSplinter.call() called");
            List<DataItem<T>> data = parent.getData();
            
            List<DataItem<T>> leftLeaf = new ArrayList<DataItem<T>>();
            List<DataItem<T>> rightLeaf = new ArrayList<DataItem<T>>();
            for (DataItem<T> item : data)
            {
                rightLeaf.add(item);
            }
            
            // find object with highest average distance        
            int indexOfHighestAverage = splinterStart;//parent.getNextSpilt();//getIndexWithHighestAverageIndex(rightLeaf);
            
            
            // add indexOfHighestAverage to leftLeaf and rm from rightLeaf
            leftLeaf.add(rightLeaf.remove(indexOfHighestAverage));
            
            // for all items in rightLeaf see if closer to leftLeaf
            for (int i = 0; i < data.size()-1; i++)
            {
                int rightLeafItemIndex = (i - ((data.size()-1) - rightLeaf.size()));
                double avarageRight = calculateAverage(rightLeaf, rightLeafItemIndex);
                leftLeaf.add(rightLeaf.get(rightLeafItemIndex));
                double avarageLeft = calculateAverage(leftLeaf, leftLeaf.size()-1);
                
                if (avarageLeft < avarageRight)
                {
                    rightLeaf.remove(rightLeafItemIndex);
                }
                else
                {
                    leftLeaf.remove(leftLeaf.size()-1);
                }
            }
            
            TreeNode<T> leftTreeNode = new TreeNode<T>(leftLeaf, parent );
            TreeNode<T> rightTreeNode = new TreeNode<T>(rightLeaf, parent);
            parent.setChildren(leftTreeNode, rightTreeNode);
            leftTreeNode.calculateSplits();
            rightTreeNode.calculateSplits();
            leftTreeNode.setToBeSplitOn(getIndexFurthestPoints(leftTreeNode));
        rightTreeNode.setToBeSplitOn(getIndexFurthestPoints(rightTreeNode));
            
            //System.out.println("createNewSplinter : leftTreeNode " + leftLeaf.size() + " rightTreeNode " + rightLeaf.size());            
            
            List<TreeNode<T>> leafNodes = findLeafNodes(parent);
            int largestDiameterIndex = getClusterIndexWithLargestDiameter(leafNodes);            
            return leafNodes.get(largestDiameterIndex);
            
            //return root;
        }
    };*/


/*  System.out.println("Create createNewSplinter future");
            scavengerContext = null;
            Future<TreeNode<T>> future = null;
            ScavengerFunction<TreeNode<T>> run = new CreateNewSplinter();
            Algorithm<TreeNode<T>, TreeNode<T>> algorithm = scavengerAlgorithm.expensive("createNewSplinter", run).cacheGlobally();
            Computation<TreeNode<T>> computation = scavengerComputation.apply("node_"+root+root.getToBeSplitOn().peek(), root).cacheGlobally();
            List<Future<TreeNode<T>>> futures = new ArrayList<Future<TreeNode<T>>>();
            for (int i = root.getSplitNumber(); i < numberOfIterations; i++)
            {
                computation = algorithm.apply(computation);
                future = scavengerContext().submit(computation);            
            }
            try
            {
                result = (TreeNode<T>)Await.result(future, (new Timeout(Duration.create(40, "seconds")).duration()));
            }
            catch(Exception e) 
            { 
                e.printStackTrace(); 
            }
            scavengerContext = scavengerContext();
   
            result = findRoot(result);*/
// List<TreeNode<T>> leafNodes = new ArrayList<TreeNode<T>>();
       // leafNodes.add(root);
        
        /* Original
        for (int i = 0; i < numberOfIterations; i++)
        {           
            int largestDiameterIndex = getClusterIndexWithLargestDiameter(leafNodes);
            //TreeNode node = createNewSplinter(leafNodes.remove(largestDiameterIndex)); 
            TreeNode node = leafNodes.remove(largestDiameterIndex); // will no longer be a leaf node
            node = runCreateNewSplinterJob(node); 
            leafNodes.add(node.getChildLeft());
            leafNodes.add(node.getChildRight());
        }
        */

 /* private TreeNode<T> runWithDescisions(TreeNode<T> parent, int index)
    {        
        *DescisionPoint currentPoint = descisionPoints.get(descisionPoints.size()-1);
        TreeNode<T> parent = currentPoint.treeNode;
        //for (int i = currentPoint.iteration; i < numberOfIterations; i++)
        //{
            DescisionPoint dp = new DescisionPoint();        
            dp.treeNode = parent;
            dp.indexItems = getIndexFurthestPoints(parent);
            dp.iteration = i;
            descisionPoints.add(dp);*
            
            //CreateNewSplinter
            
            ScavengerFunction<TreeNode<T>> run = new CreateNewSplinter<T>(index);
            Computation<TreeNode<T>> computationData = scavengerComputation.apply("parent"+parent, parent).cacheGlobally();
            Algorithm<TreeNode<T>, TreeNode<T>> algorithm = scavengerAlgorithm.expensive("createNewSplinter", run).cacheGlobally();
            Computation<TreeNode<T>> computation1 = algorithm.apply(computationData);
            Future<TreeNode<T>> future = scavengerContext().submit(computation1);
            
            try
            {
                parent = (TreeNode<T>)Await.result(future, (new Timeout(Duration.create(40, "seconds")).duration()));
            }
            catch(Exception e) 
            { 
                e.printStackTrace(); 
            }
            if (currentPoint.indexItems.size() == 0)
            {
                descisionPoints.remove(descisionPoints.size()-1);
            }
        //}
        return parent;        
    }
    class DescisionPoint
    {
        TreeNode<T> treeNode; 
        List<TreeNode<T>> leafNodes;
        List<Integer> indexItems; //list of items that could form the starting point of a new cluster
        int iteration;
    }
    
    private List<Integer> getIndexFurthestPoints(TreeNode<T> cluster)
    {
        int numberOfPoints = 4;
        
        List<Integer> indexsOfHighestAverage = new ArrayList<Integer>();
        List<Double> highestAverages = new ArrayList<Double>();
        for (int i = 0; i < cluster.size(); i++)
        {
            
            double average = calculateAverage(cluster, i); 
            
            if (i < numberOfPoints)
            {
                indexsOfHighestAverage.add(i);
                highestAverege.add(i);
            }
            int smallestIndex = getSmallest(highestAverages);
            if (average > highestAverages.get(smallestIndex))
            {
                indexsOfHighestAverage.set(smallestIndex, i);
                highestAverages.set(smallestIndex, average);
            }
        }
        return indexsOfHighestAverage;
    }
    
    private int getSmallest(List<Double> list)
    {
        int smallestIndex = 0;
        double smallestValue = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++)
        {
            if(smallestValue > list.get(i))
            {
                smallestIndex = i;
                smallestValue = list.get(i);
            }
        }
        return smallestIndex;
    }*/


   /* class CreateNewSplinter<T> extends ScavengerFunction<TreeNode<T>> 
    {
        private int indexOfInitialItem;
        public CreateNewSplinter(int indexOfInitialItem)
        {
            indexOfInitialItem = indexOfInitialItem;
        }
        
        public TreeNode<T> call()
        {
            TreeNode<T> parent = value;
            //TreeNode parent = value;
            System.out.println("CreateNewSplinter.call() called");
            List<DataItem<T>> data = parent.getData();
            
            List<DataItem<T>> leftLeaf = new ArrayList<DataItem<T>>();
            List<DataItem<T>> rightLeaf = new ArrayList<DataItem<T>>();
            
            // add all items to the rightLeaf (the leaftLeaf is the "splinter" cluster)
            for (DataItem<T> item : data)
            {
                rightLeaf.add(item);
            }
            
            // find object with highest average distance        
            int indexOfHighestAverage = indexOfInitialItem;//getIndexWithHighestAverageIndex(rightLeaf);
            System.out.println("runCreateNewSplinterJob indexOfHighestAverage : " + indexOfHighestAverage );
            // add indexOfHighestAverage to leftLeaf and rm from rightLeaf
            leftLeaf.add(rightLeaf.remove(indexOfHighestAverage));
            
            double largestDistanceAverage = 0;
            while (largestDistanceAverage >= 0)
            {
                largestDistanceAverage = -Double.MAX_VALUE;
                int indexLargestDistanceAverage = 0;
                // Find the data item who most belongs to the splinter group
                //    Where most belongs is : The item with the hightest (Item average distance to group (rightLeaf)) - (Item average distance to splinter group (leftLeaf))
                // This item is then removed from the group (rightLeaf) and added to the splinter group (leftLeaf)
                for (int i = 0; i < rightLeaf.size(); i++)
                {
                    leftLeaf.add(rightLeaf.get(i));
                    // calculate the average of item leftLeaf
                    double avarageLeft = calculateAverage(leftLeaf, leftLeaf.size()-1);
                    // calculate the average of item rightLeaf
                    double avarageRight = calculateAverage(rightLeaf, i);
                    //System.out.println("avarageLeft : " + avarageLeft);
                    //System.out.println("avarageRight : " + avarageRight);
                    
                    double diff = avarageRight - avarageLeft;
                    if (diff > largestDistanceAverage)
                    {
                        //System.out.println("set indexLargestDistanceAverage : " + indexLargestDistanceAverage);
                        largestDistanceAverage = diff;
                        indexLargestDistanceAverage = i;
                    }
                    leftLeaf.remove(leftLeaf.size()-1);
                }
                //System.out.println("end indexLargestDistanceAverage : " + indexLargestDistanceAverage);
                //System.out.println("end largestDistanceAverage : " + largestDistanceAverage);
                if(largestDistanceAverage >= 0.0)
                {
                    leftLeaf.add(rightLeaf.remove(indexLargestDistanceAverage));
                }
            }
            
            TreeNode<T> leftTreeNode = new TreeNode<T>(leftLeaf, parent);
            TreeNode<T> rightTreeNode = new TreeNode<T>(rightLeaf, parent);
            parent.setChildren(leftTreeNode, rightTreeNode);
            
            //System.out.println("createNewSplinter : leftTreeNode " + leftLeaf.size() + " rightTreeNode " + rightLeaf.size());            
            return parent; 
        }
    }*/

   
    /**
     * Splits the data in the parent (given TreeNode) into two clusters
     *
     * @param The TreeNode who's data will be split into two clusters
     * @return  The TreeNode with it's children set to the new clusters.
     */
   /* private TreeNode<T> runCreateNewSplinterJob(TreeNode<T> parent)
    { 
        //TreeNode parent = value;
        System.out.println("CreateNewSplinter.call() called");
        List<DataItem<T>> data = parent.getData();
        
        List<DataItem<T>> leftLeaf = new ArrayList<DataItem<T>>();
        List<DataItem<T>> rightLeaf = new ArrayList<DataItem<T>>();
        
        // add all items to the rightLeaf (the leaftLeaf is the "splinter" cluster)
        for (DataItem<T> item : data)
        {
            rightLeaf.add(item);
        }
        
        // find object with highest average distance        
        int indexOfHighestAverage = getIndexWithHighestAverageIndex(rightLeaf);
        
                System.out.println("runCreateNewSplinterJob indexOfHighestAverage : " + indexOfHighestAverage );
                System.out.println("runCreateNewSplinterJob parent : " + parent.getToBeSplitOn() );
        // add indexOfHighestAverage to leftLeaf and rm from rightLeaf
        leftLeaf.add(rightLeaf.remove(indexOfHighestAverage));
        
        double largestDistanceAverage = 0;
        while (largestDistanceAverage >= 0)
        {
            largestDistanceAverage = -Double.MAX_VALUE;
            int indexLargestDistanceAverage = 0;
            // Find the data item who most belongs to the splinter group
            //    Where most belongs is : The item with the hightest (Item average distance to group (rightLeaf)) - (Item average distance to splinter group (leftLeaf))
            // This item is then removed from the group (rightLeaf) and added to the splinter group (leftLeaf)
            for (int i = 0; i < rightLeaf.size(); i++)
            {
                leftLeaf.add(rightLeaf.get(i));
                // calculate the average of item leftLeaf
                double avarageLeft = calculateAverage(leftLeaf, leftLeaf.size()-1);
                // calculate the average of item rightLeaf
                double avarageRight = calculateAverage(rightLeaf, i);
                //System.out.println("avarageLeft : " + avarageLeft);
                //System.out.println("avarageRight : " + avarageRight);
                
                double diff = avarageRight - avarageLeft;
                if (diff > largestDistanceAverage)
                {
                    //System.out.println("set indexLargestDistanceAverage : " + indexLargestDistanceAverage);
                    largestDistanceAverage = diff;
                    indexLargestDistanceAverage = i;
                }
                leftLeaf.remove(leftLeaf.size()-1);
            }
            //System.out.println("end indexLargestDistanceAverage : " + indexLargestDistanceAverage);
            //System.out.println("end largestDistanceAverage : " + largestDistanceAverage);
            if(largestDistanceAverage >= 0.0)
            {
                leftLeaf.add(rightLeaf.remove(indexLargestDistanceAverage));
            }
        }
        
        TreeNode<T> leftTreeNode = new TreeNode<T>(leftLeaf, parent, this);
        TreeNode<T> rightTreeNode = new TreeNode<T>(rightLeaf, parent, this);
        parent.setChildren(leftTreeNode, rightTreeNode);
        
        //System.out.println("createNewSplinter : leftTreeNode " + leftLeaf.size() + " rightTreeNode " + rightLeaf.size());            
        return parent;        
    }*/
    
    
    
    
    // The creatation of a splinter had been setup to use scavenger, but at the moment pointless as we always wait for a single future to return, and CreateNewSplinter is never called on the same data.
   /* private TreeNode<T> runCreateNewSplinterJob(TreeNode<T> parent)
    {
        System.out.println("runCreateNewSplinterJob called");
        ScavengerFunction<TreeNode<T>> run = new CreateNewSplinter<T>();
        Computation<TreeNode<T>> computationData = scavengerComputation.apply("parent"+parent, parent).cacheGlobally();
        Algorithm<TreeNode<T>, TreeNode<T>> algorithm = scavengerAlgorithm.expensive("createNewSplinter", run).cacheGlobally();
        Computation<TreeNode<T>> computation1 = algorithm.apply(computationData);
        Future<TreeNode<T>> future = scavengerContext().submit(computation1);
        
        try
        {
            parent = (TreeNode<T>)Await.result(future, (new Timeout(Duration.create(40, "seconds")).duration()));
        }
        catch(Exception e) 
        { 
            e.printStackTrace(); 
        }
        return parent;
    }*/



