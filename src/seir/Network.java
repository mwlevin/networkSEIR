/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package seir;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;

/**
 *
 * @author micha
 */
public class Network 
{
    public static final boolean optimizeParameters = false;
    public static final double INFTY = Double.MAX_VALUE;
    
    private double inv_sigma = 1.0/12; // incubation time
    private double inv_ell = 1.0/7; // recovery time
    
    private double gradient_inv_sigma, gradient_inv_ell;
    
    private double xi = 0.5; // reduction in travel among infected individuals
    private double gradient_xi;
    
    private int T;
    
    private Zone[] zones;
    private Link[][] matrix;
    
    private TimePeriod[] lambda_periods, r_periods;
    
    
    
    
    
    

    
    public Network(String scenario) throws IOException
    {
        readNetwork(scenario);
    }
    
    public void save(File file) throws IOException
    {
        PrintStream fileout = new PrintStream(new FileOutputStream(file), true);
        
        fileout.println(inv_sigma+"\t"+inv_ell+"\t"+xi);
        for(Zone i : zones)
        {
            for(int pi = 0; pi < lambda_periods.length; pi++)
            {
                fileout.print(i.lambda[pi]+"\t");
            }
            for(int pi = 0; pi < r_periods.length; pi++)
            {
                fileout.print(i.r[pi]+"\t");
            }
            fileout.print(i.E0+"\n");
        }
        
        fileout.close();
    }
    
    public void load(File file) throws IOException
    {
        Scanner filein = new Scanner(file);
        
        inv_sigma = filein.nextDouble();
        inv_ell = filein.nextDouble();
        xi = filein.nextDouble();
        
        for(Zone i : zones)
        {
            for(int pi = 0; pi < lambda_periods.length; pi++)
            {
                i.lambda[pi] = filein.nextDouble();
            }
            for(int pi = 0; pi < r_periods.length; pi++)
            {
                i.r[pi] = filein.nextDouble();
            }
            i.E0 = filein.nextDouble();
        }
        filein.close();
    }
    
    
    public void readNetwork(String dir) throws IOException
    {
        
        boolean onlyFirst = false;
        
        Scanner filein = new Scanner(new File("data/"+dir+"/MN_population.csv"));
        int count = 0;
        while(filein.hasNext())
        {
            count++;
            filein.nextLine();
        }
        filein.close();
        
        if(onlyFirst)
        {
            count = 1;
        }
        
        zones = new Zone[count];
        
        filein = new Scanner(new File("data/"+dir+"/MN_population.csv"));
        
        
        int idx = 0;
        while(filein.hasNext())
        {
            Scanner chopper = new Scanner(filein.nextLine());
            chopper.useDelimiter(",");
            
            int county = chopper.nextInt();
            double pop = chopper.nextDouble();
            zones[idx++] = new Zone(county, pop);
            
            if(onlyFirst)
            {
                break;
            }
        }
        filein.close();
        
        count = 0;
        filein = new Scanner(new File("data/"+dir+"/MN_infected.csv"));
        while(filein.hasNext())
        {
            filein.nextLine();
            count++;
        }
        filein.close();
        
        T = count;
        
        filein = new Scanner(new File("data/"+dir+"/MN_infected.csv"));
        double[][] reportedI = new double[zones.length][T];
        
        Map<Integer, Integer> cols = new HashMap<>();

        
        Scanner chopper = new Scanner(filein.nextLine());
        chopper.useDelimiter(",");
        
        chopper.next();
        
        idx = 0;
        while(chopper.hasNextInt())
        {
            cols.put(chopper.nextInt(), idx++);
        }
        
        while(filein.hasNext())
        {
            chopper = new Scanner(filein.nextLine());
            chopper.useDelimiter(",");
            
            int t = chopper.nextInt();
            
            for(int i = 0; i < reportedI.length; i++)
            {
                reportedI[i][t-1] = chopper.nextDouble();
            }
        }
        
        filein.close();
        
        for(Zone z : zones)
        {
            z.setReportedI(reportedI[cols.get(z.getId())]);
        }
        
        
        reportedI = null;
        
        
        filein = new Scanner(new File("data/"+dir+"/MN_recovered.csv"));
        double[][] reportedR = new double[zones.length][T];
        
        cols = new HashMap<>();

        
        chopper = new Scanner(filein.nextLine());
        chopper.useDelimiter(",");
        
        chopper.next();
        
        idx = 0;
        while(chopper.hasNextInt())
        {
            cols.put(chopper.nextInt(), idx++);
        }
        
        while(filein.hasNext())
        {
            chopper = new Scanner(filein.nextLine());
            chopper.useDelimiter(",");
            
            int t = chopper.nextInt();
            
            for(int i = 0; i < reportedR.length; i++)
            {
                reportedR[i][t-1] = chopper.nextDouble();
            }
        }
        
        filein.close();
        
        for(Zone z : zones)
        {
            z.setReportedR(reportedR[cols.get(z.getId())]);
        }
        
        
        filein = new Scanner(new File("data/"+dir+"/MN_deaths.csv"));
        reportedR = new double[zones.length][T];
        
        cols = new HashMap<>();

        
        chopper = new Scanner(filein.nextLine());
        chopper.useDelimiter(",");
        
        chopper.next();
        
        idx = 0;
        while(chopper.hasNextInt())
        {
            cols.put(chopper.nextInt(), idx++);
        }
        
        while(filein.hasNext())
        {
            chopper = new Scanner(filein.nextLine());
            chopper.useDelimiter(",");
            
            int t = chopper.nextInt();
            
            for(int i = 0; i < reportedR.length; i++)
            {
                reportedR[i][t-1] = chopper.nextDouble();
            }
        }
        
        filein.close();
        
        for(Zone z : zones)
        {
            z.addReportedR(reportedR[cols.get(z.getId())]);
        }
        
        
        
        matrix = new Link[zones.length][zones.length];
        
        for(int r = 0; r < matrix.length; r++)
        {
            for(int c = 0; c < matrix[r].length; c++)
            {
                if(r != c)
                {
                    matrix[r][c] = new Link();
                }
            }
        }
        
        
        
        
        
        filein = new Scanner(new File("data/"+dir+"/timeline_r.txt"));
        
        Date start = null;
        
        List<Integer> timeline = new ArrayList<>();
        
        try
        {
            start = new SimpleDateFormat("MM/dd/yyyy").parse(filein.nextLine().trim()); 
            timeline.add(0);
        }
        catch(ParseException ex)
        {
            ex.printStackTrace(System.err);
        }
        
        
        
        while(filein.hasNext())
        {
            try
            {
                Date date = new SimpleDateFormat("MM/dd/yyyy").parse(filein.nextLine().trim()); 
                timeline.add(daysBetween(start, date));
            }
            catch(ParseException ex)
            {
                ex.printStackTrace(System.err);
            }
        }
        filein.close();
        
        timeline.add(T+1);

        Collections.sort(timeline);
        
        r_periods = new TimePeriod[timeline.size()-1];
        
        for(int i = 0; i < timeline.size()-1; i++)
        {
            r_periods[i] = new TimePeriod(timeline.get(i), timeline.get(i+1));
        }
        
        
        
        
        filein = new Scanner(new File("data/"+dir+"/timeline_lambda.txt"));
        
        start = null;
        
        timeline = new ArrayList<>();
        
        try
        {
            start = new SimpleDateFormat("MM/dd/yyyy").parse(filein.nextLine().trim()); 
            timeline.add(0);
        }
        catch(ParseException ex)
        {
            ex.printStackTrace(System.err);
        }
        
        
        
        while(filein.hasNext())
        {
            try
            {
                Date date = new SimpleDateFormat("MM/dd/yyyy").parse(filein.nextLine().trim()); 
                timeline.add(daysBetween(start, date));
            }
            catch(ParseException ex)
            {
                ex.printStackTrace(System.err);
            }
        }
        filein.close();
        
        timeline.add(T+1);

        Collections.sort(timeline);
        
        lambda_periods = new TimePeriod[timeline.size()-1];
        
        for(int i = 0; i < timeline.size()-1; i++)
        {
            lambda_periods[i] = new TimePeriod(timeline.get(i), timeline.get(i+1));
        }

    }
    
    public int daysBetween(Date d1, Date d2){
             return (int)( (d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
     }
    
    public int index_r(int t)
    {
        for(int i = 0; i < r_periods.length; i++)
        {
            if(r_periods[i].contains(t))
            {
                return i;
            }
        }
        return -1;
    }
    
    public int index_lambda(int t)
    {
        for(int i = 0; i < lambda_periods.length; i++)
        {
            if(lambda_periods[i].contains(t))
            {
                return i;
            }
        }
        return -1;
    }
    
    public void printSolution()
    {
        System.out.println("Zone\ttime\tpredicted\treported\terror\tr\tlambda");
        for(Zone i : zones)
        {
            for(int t = 0; t < T; t++)
            {
                System.out.println(i.getId()+"\t"+t+"\t"+String.format("%.2f", i.I[t])+"\t"+
                    String.format("%.2f", i.lambda[index_lambda(t)] * i.reportedI[t])+"\t"+
                    String.format("%.2f", Math.abs(i.I[t]-i.lambda[index_lambda(t)] * i.reportedI[t]))+"\t"+
                    String.format("%.2f", i.lambda[index_lambda(t)])+"\t"+
                    String.format("%.2f", i.r[index_r(t)]));
            }
        }
    }
    
    public void printTotalError()
    {
        System.out.println("time\ttotal error");
        for(int t = 0; t < T; t++)
        {
            double error = 0;
            
            int pi = index_lambda(t);
            
            for(Zone i : zones)
            {
                error += Math.abs( i.I[t] - i.lambda[index_lambda(t)] * i.reportedI[t]);
            }
            System.out.println(t+"\t"+error);
        }
    }
    
    int num_iter = 100;
    double alpha = 0.4;
    double beta = 0.5;
    double min_improvement = 0.01;
    
    public void gradientDescent(File saveFile) throws IOException 
    {
        for(Zone z : zones)
        {
            z.initialize(T, r_periods, lambda_periods);
        }
        
        for(int r = 0; r < matrix.length; r++)
        {
            for(int c = 0; c < matrix[r].length; c++)
            {
                if(r != c)
                {
                    matrix[r][c].initialize(T);
                }
            }
        }
        
        
        
        System.out.println("T = "+T);
        System.out.println("lambda_periods = "+lambda_periods.length);
        System.out.println("r_periods = "+r_periods.length);
        
        // initial solution
        for(Zone i : zones)
        {
            for(int pi = 0; pi < lambda_periods.length; pi++)
            {
                i.lambda[pi] = 1;
            }
            
            for(int pi = 0; pi < r_periods.length; pi++)
            {
                i.r[pi] = 1;
            }
            
            i.E0 = 1;
        }
        
        
        
        
        
        
        System.out.println("Iteration\tObjective\tObj. change\tError\tCPU time (s)");
        
        double obj = calculateSEIR();
        double improvement = 100;
        
        System.out.println("0\t"+obj);
        
        double prev_obj = obj;
        
        for(int iter = 1; iter <= num_iter && improvement > min_improvement; iter++)
        {   
            long time = System.nanoTime();
            
            double step = 0;
            for(Zone i : zones)
            {
                resetGradients();
                calculateGradient_lambda(i);
                
                step = calculateStep(iter, obj);
                updateVariables(step);
                obj = calculateSEIR();
                
                resetGradients();
                calculateGradient_r(i);
                
                step = calculateStep(iter, obj);
                updateVariables(step);
                obj = calculateSEIR();
                
                resetGradients();
                calculateGradient_E0(i);

                step = calculateStep(iter, obj);
                updateVariables(step);
                obj = calculateSEIR();
                
                //System.out.println("\t"+obj+"\t"+step);
            }
            
            if(optimizeParameters)
            {
                System.out.print("Before: "+obj);

                resetGradients();

                calculateGradient_ell();
                calculateGradient_sigma();
                calculateGradient_xi();

                step = calculateStep(iter, obj);
                updateVariables(step);
                obj = calculateSEIR();

                System.out.print("\tAfter: "+obj+"\n");
            }
            
            
            time = System.nanoTime() - time;
            // System.out.println("Step: "+step);
            //System.out.println("Max step: "+calculateMaxStep());
            //System.out.println("Obj: "+obj);
            improvement = 100.0*(prev_obj - obj) / prev_obj;
            
            double error = calculateInfectedError();
            
            System.out.println(iter+"\t"+obj+"\t"+String.format("%.2f", improvement)
                    +"%\t"+String.format("%.2f", error)+"%\t"+String.format("%.1f", time/1.0e9));
            prev_obj = obj;
            
            save(saveFile);
        }
        
    }
    
    public double calculateInfectedError()
    {
        double error = 0.0;
        double total = 0.0;
        
        for(int t = 0; t < T; t++)
        {
            int pi = index_lambda(t);
            
            for(Zone i : zones)
            {
                total += i.reportedI[t];
                error += Math.abs(i.I[t] - i.lambda[pi] * i.reportedI[t]);
            }
        }
        
        return 100.0*error/total;
    }
    
    public double calculateStep(int iter, double obj)
    {
        double step = 1;
        //step = calculateMaxStep();

        double change = gradDotX();

        while(calculateSEIRsearch(step, iter) > obj + alpha * step * change)
        {
            step = step*beta;

            //System.out.println("\t"+calculateSEIRsearch(step, iter)+" > "+(obj + alpha * step * change));
        }

        //System.out.println("\t"+calculateSEIRsearch(step, iter)+" > "+(obj + alpha * step * change));
            
        return step;
    }
    
    public void resetGradients()
    {
        gradient_inv_sigma = 0;
        gradient_inv_ell = 0;
        gradient_xi = 0;
        
        for(Zone i : zones)
        {
            for(int pi = 0; pi < lambda_periods.length; pi++)
            {
                i.gradient_lambda[pi] = 0;
            }
            
            for(int pi = 0; pi < r_periods.length; pi++)
            {
                i.gradient_r[pi] = 0;
            }
            
            i.gradient_E0 = 0;
        }
    }
    
    public void updateVariables(double step)
    {
        xi = xi - step*gradient_xi;
        inv_sigma = inv_sigma - step*gradient_inv_sigma;
        inv_ell = inv_ell - step*gradient_inv_ell;
        
        for(Zone i : zones)
        {
            for(int pi = 0; pi < lambda_periods.length; pi++)
            {
                i.lambda[pi] = Math.max(1, i.lambda[pi] - step * i.gradient_lambda[pi]);
            }
            
            for(int pi = 0; pi < r_periods.length; pi++)
            {
                i.r[pi] = Math.min(20, Math.max(0, i.r[pi] - step * i.gradient_r[pi]));
            }
            
            i.E0 = Math.min(i.getN()/100, Math.max(0, i.E0 - step * i.gradient_E0));
            
            i.I[0] = i.lambda[0] * i.reportedI[0];
            
        }
    }
    
    // calculate the maximum step size based on feasibility (r>=0, lambda>=1, E0>=0, E0<=N-I0)
    public double calculateMaxStep()
    {
        double output = 1;
        
        for(Zone i : zones)
        {
            for(int pi = 0; pi < lambda_periods.length; pi++)
            {
                // newlambda = lambda - step*gradient
                double temp = (i.lambda[pi] - 1)/i.gradient_lambda[pi];
                if(temp > 0)
                {
                    output = Math.min(output, temp);
                }
            }
            
            for(int pi = 0; pi < r_periods.length; pi++)
            {
                // newr = r - step*gradient
                double temp = (i.r[pi] - 0)/i.gradient_r[pi];
                if(temp > 0)
                {
                    output = Math.min(output, temp);
                }
            }
            
            // new E0 = E0 - step*gradient
            double temp = (i.E0 - 0)/i.gradient_E0;
            if(temp > 0)
            {
                output = Math.min(output, temp);
            }
        }
        
        return output;
    }
    
    public void calculateGradient_lambda()
    {
        
        // calculate dZ/d lambda_i
        for(Zone i : zones)
        {
            calculateGradient_lambda(i);
        }
    }
    
    public void calculateGradient_sigma()
    {
        for(Zone i : zones)
        {
            i.resetDerivs();
        }
        
        for(int t = 0; t < T-1; t++)
        {
            int pi = index_r(t);
            for(Zone i : zones)
            {
                double dN = i.dS[t] + i.dE[t] + i.dI[t] + i.dR[t];
                double N = i.getN(t);
                
                i.dI[t+1] = i.dI[t] + inv_sigma * i.dE[t] + inv_sigma * i.E[t] - inv_ell * i.dI[t];
                
                i.dE[t+1] = i.dE[t] + i.r[pi]*i.dS[t]*i.I[t]/N + i.r[pi]*i.S[t]/N*i.dI[t] 
                        - i.r[pi]*i.S[t]*i.I[t]/N/N*dN - inv_sigma*i.dE[t] - inv_sigma*i.E[t];
                
                i.dS[t+1] = i.dS[t] - i.r[pi]*i.dS[t]*i.I[t]/N - i.r[pi]*i.S[t]/N*i.dI[t] +
                        i.r[pi]*i.S[t]*i.I[t]/N/N*dN;
                
                i.dR[t+1] = i.dR[t] + inv_ell*i.dI[t];
                
                for(int jx = 0; jx < matrix.length; jx++)
                {
                    if(jx != i.getIdx())
                    {
                        Zone j = zones[jx];

                        i.dI[t+1] += xi*(matrix[jx][i.getIdx()].getMu(t)*j.dI[t] - matrix[i.getIdx()][jx].getMu(t)*i.dI[t]);

                        i.dE[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dE[t] - matrix[i.getIdx()][jx].getMu(t)*i.dE[t];

                        i.dS[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dS[t] - matrix[i.getIdx()][jx].getMu(t)*i.dS[t];

                        i.dR[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dR[t] - matrix[i.getIdx()][jx].getMu(t)*i.dR[t];
                    }
                }
            }
        }
        
        gradient_inv_sigma = 0;
        
        for(int t = 0; t < T; t++)
        {
            int pi = index_lambda(t);
            
            for(Zone i : zones)
            {
                gradient_inv_sigma += 2* (i.I[t] - i.lambda[pi]*i.reportedI[t])*i.dI[t];
            }
        }
    }
    
    public void calculateGradient_ell()
    {
        for(Zone i : zones)
        {
            i.resetDerivs();
        }
        
        for(int t = 0; t < T-1; t++)
        {
            int pi = index_r(t);
            for(Zone i : zones)
            {
                double dN = i.dS[t] + i.dE[t] + i.dI[t] + i.dR[t];
                double N = i.getN(t);
                
                i.dI[t+1] = i.dI[t] + inv_sigma * i.dE[t]  - inv_ell * i.dI[t] - inv_ell*i.I[t];
                
                i.dE[t+1] = i.dE[t] + i.r[pi]*i.dS[t]*i.I[t]/N + i.r[pi]*i.S[t]/N*i.dI[t] 
                        - i.r[pi]*i.S[t]*i.I[t]/N/N*dN - inv_sigma*i.dE[t];
                
                i.dS[t+1] = i.dS[t] - i.r[pi]*i.dS[t]*i.I[t]/N - i.r[pi]*i.S[t]/N*i.dI[t] +
                        i.r[pi]*i.S[t]*i.I[t]/N/N*dN;
                
                i.dR[t+1] = i.dR[t] + inv_ell*i.dI[t] + inv_ell*i.I[t];
                
                for(int jx = 0; jx < matrix.length; jx++)
                {
                    
                    if(jx != i.getIdx())
                    {
                        Zone j = zones[jx];

                        i.dI[t+1] += xi*(matrix[jx][i.getIdx()].getMu(t)*j.dI[t] - matrix[i.getIdx()][jx].getMu(t)*i.dI[t]);

                        i.dE[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dE[t] - matrix[i.getIdx()][jx].getMu(t)*i.dE[t];

                        i.dS[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dS[t] - matrix[i.getIdx()][jx].getMu(t)*i.dS[t];

                        i.dR[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dR[t] - matrix[i.getIdx()][jx].getMu(t)*i.dR[t];
                    }
                }
            }
        }
        
        gradient_inv_ell = 0;
        
        for(int t = 0; t < T; t++)
        {
            int pi = index_lambda(t);
            
            for(Zone i : zones)
            {
                gradient_inv_ell += 2* (i.I[t] - i.lambda[pi]*i.reportedI[t])*i.dI[t];
            }
        }
    }
    
    
    public void calculateGradient_xi()
    {
        for(Zone i : zones)
        {
            i.resetDerivs();
        }
        
        for(int t = 0; t < T-1; t++)
        {
            int pi = index_r(t);
            for(Zone i : zones)
            {
                double dN = i.dS[t] + i.dE[t] + i.dI[t] + i.dR[t];
                double N = i.getN(t);
                
                i.dI[t+1] = i.dI[t] + inv_sigma * i.dE[t]  - inv_ell * i.dI[t];
                
                i.dE[t+1] = i.dE[t] + i.r[pi]*i.dS[t]*i.I[t]/N + i.r[pi]*i.S[t]/N*i.dI[t] 
                        - i.r[pi]*i.S[t]*i.I[t]/N/N*dN - inv_sigma*i.dE[t];
                
                i.dS[t+1] = i.dS[t] - i.r[pi]*i.dS[t]*i.I[t]/N - i.r[pi]*i.S[t]/N*i.dI[t] +
                        i.r[pi]*i.S[t]*i.I[t]/N/N*dN;
                
                i.dR[t+1] = i.dR[t] + inv_ell*i.dI[t];
                
                for(int jx = 0; jx < matrix.length; jx++)
                {
                    if(jx != i.getIdx())
                    {
                        Zone j = zones[jx];

                        i.dI[t+1] += xi*(matrix[jx][i.getIdx()].getMu(t)*j.dI[t] - matrix[i.getIdx()][jx].getMu(t)*i.dI[t]);
                        i.dI[t+1] += (matrix[jx][i.getIdx()].getMu(t)*j.I[t] - matrix[i.getIdx()][jx].getMu(t)*i.I[t]);

                        i.dE[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dE[t] - matrix[i.getIdx()][jx].getMu(t)*i.dE[t];

                        i.dS[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dS[t] - matrix[i.getIdx()][jx].getMu(t)*i.dS[t];

                        i.dR[t+1] += matrix[jx][i.getIdx()].getMu(t)*j.dR[t] - matrix[i.getIdx()][jx].getMu(t)*i.dR[t];
                    }
                }
            }
        }
        
        gradient_xi = 0;
        
        for(int t = 0; t < T; t++)
        {
            int pi = index_lambda(t);
            
            for(Zone i : zones)
            {
                gradient_xi += 2* (i.I[t] - i.lambda[pi]*i.reportedI[t])*i.dI[t];
            }
        }
    }
    
    public void calculateGradient_lambda(Zone i)
    {
        for(int pi = 0; pi < lambda_periods.length; pi++)
        {
            double sum = 0;

            for(int t = lambda_periods[pi].getStart(); t < lambda_periods[pi].getEnd() && t < T; t++)
            {
                sum += 2* (i.I[t] - i.lambda[pi] * i.reportedI[t]) * i.reportedI[t];
            }

            i.gradient_lambda[pi] = sum;
        }
    }
    
    public void calculateGradient_r()
    {
        
        // calculate dZ/dr_i(pi)
        for(Zone i : zones)
        {
            calculateGradient_r(i);
        }
    }
    
    public void calculateGradient_r(Zone i)
    {
        for(int pix = 0; pix < r_periods.length; pix++)
        {
            for(Zone j : zones)
            {
                j.resetDerivs();
            }

            TimePeriod pi = r_periods[pix];


            for(int t = 0; t < T-1; t++)
            {
                int t_idx = index_r(t);

                for(int jx = 0; jx < matrix.length; jx++)
                {
                    Zone j = zones[jx];

                    j.dI[t+1] = j.dI[t] + inv_sigma * j.dE[t] - inv_ell * j.dI[t];

                    double drdr = 0;

                    if(i == j && pi.contains(t))
                    {
                        drdr = 1;
                    }

                    double N = j.getN(t);
                    double dN = j.dS[t] + j.dE[t] + j.dI[t] + j.dR[t];
                    j.dE[t+1] = j.dE[t] + drdr * j.S[t] * j.I[t]/j.getN(t) + j.r[t_idx]*j.dS[t]*j.I[t]/N
                            + j.r[t_idx] * j.S[t]/N * j.dI[t] - j.r[t_idx]*j.S[t]*j.I[t]/N/N*dN
                            - inv_sigma * j.dE[t];

                    j.dS[t+1] = j.dS[t] - drdr * j.S[t] * j.I[t]/N - j.r[t_idx]*j.dS[t]*j.I[t]/N
                            + j.r[t_idx]*j.S[t]*j.I[t]/N/N*dN - j.r[t_idx] * j.S[t]/N * j.dI[t];

                    j.dR[t+1] = j.dR[t] + inv_ell*j.dI[t];

                    for(int kx = 0; kx < matrix.length; kx++)
                    {
                        if(jx != kx)
                        {
                            Zone k = zones[kx];

                            j.dI[t+1] += xi * (matrix[kx][jx].getMu(t)*k.dI[t] - matrix[jx][kx].getMu(t)*j.dI[t]);

                            j.dE[t+1] += matrix[kx][jx].getMu(t)*k.dE[t] - matrix[jx][kx].getMu(t-1)*j.dE[t];

                            j.dS[t+1] += matrix[kx][jx].getMu(t)*k.dS[t] - matrix[jx][kx].getMu(t)*j.dS[t];

                            j.dR[t+1] += matrix[kx][jx].getMu(t)*k.dR[t] - matrix[jx][kx].getMu(t)*j.dR[t];
                        }
                    }



                }
            }


            i.gradient_r[pix] = 0;

            for(int t = 0; t < T; t++)
            {
                int t_idx = index_lambda(t);

                for(Zone j : zones)
                {
                    i.gradient_r[pix] += 2*(j.I[t] - j.lambda[t_idx] * j.reportedI[t])* j.dI[t];

                }
            }

            //System.out.println(i.gradient_r[pix]);
        }
    }
    
    public void calculateGradient_E0()
    {
        
        // calculate dZ/dE[0]
        for(Zone i : zones)
        {
            calculateGradient_E0(i);
        }
        
    }
    
    public void calculateGradient_E0(Zone i)
    {
        for(Zone j : zones)
        {
            j.resetDerivs();
        }

        i.dE[0] = 1;

        for(int t = 0; t < T-1; t++)
        {
            int t_idx = index_r(t);

            for(int jx = 0; jx < matrix.length; jx++)
            {
                Zone j = zones[jx];

                j.dI[t+1] = j.dI[t] + inv_sigma * j.dE[t] - inv_ell * j.dI[t];


                j.dE[t+1] = j.dE[t] + j.r[t_idx]*j.dS[t]*j.I[t]/j.getN()
                        + j.r[t_idx] * j.S[t]/j.getN() * j.dI[t] - inv_sigma * j.dE[t];

                j.dS[t+1] = j.dS[t] - j.r[t_idx]*j.dS[t]*j.I[t]/j.getN()
                        - j.r[t_idx] * j.S[t]/j.getN() * j.dI[t];

                for(int kx = 0; kx < matrix.length; kx++)
                {
                    if(jx != kx)
                    {
                        Zone k = zones[kx];

                        j.dI[t+1] += xi * (matrix[kx][jx].getMu(t)*k.dI[t] - matrix[jx][kx].getMu(t)*j.dI[t]);

                        j.dE[t+1] += matrix[kx][jx].getMu(t)*k.dE[t] - matrix[jx][kx].getMu(t-1)*j.dE[t];

                        j.dS[t+1] += matrix[kx][jx].getMu(t)*k.dS[t] - matrix[jx][kx].getMu(t)*j.dS[t];
                    }
                }



            }
        }

        i.gradient_E0 = 0;

        for(int t = 0; t < T; t++)
        {
            int pi = index_lambda(t);

            for(Zone j : zones)
            {
                i.gradient_E0 += 2*(j.I[t] - j.lambda[pi]*j.reportedI[t]) * j.dI[t];


            }
        }

        //System.out.println(i.gradient_E0);
    }
    
    public double gradDotX()
    {
        double output = 0;
        
        for(Zone i : zones)
        {
            for(int pix = 0; pix < lambda_periods.length; pix++)
            {
                output += i.gradient_lambda[pix] * -i.gradient_lambda[pix];
            }
            
            for(int pix = 0; pix < r_periods.length; pix++)
            {
                output += i.gradient_r[pix] * -i.gradient_r[pix];
            }
            
            output += i.gradient_E0 * -i.gradient_E0;
        }
        
        return output;
    }
    
    
    
    public double calculateSEIR()
    {
        for(Zone i : zones)
        {
            i.I[0] = i.lambda[0] * i.reportedI[0];
            i.E[0] = i.E0;
            i.R[0] = 0;
            i.S[0] = i.getN() - i.I[0] - i.R[0] - i.E[0];
        }
        
        for(int t = 0; t < T-1; t++)
        {
            int pi_r = index_r(t);
            
            for(int ix = 0; ix < zones.length; ix++)
            {
                Zone i = zones[ix];
                
                double fSE = Math.min(i.S[t], i.r[pi_r] * i.S[t] * i.I[t]/i.getN(t));
                
                i.S[t+1] = i.S[t] - fSE;
                
                i.E[t+1] = i.E[t] + fSE - inv_sigma*i.E[t];
                
                i.I[t+1] = i.I[t] + inv_sigma*i.E[t] - inv_ell*i.I[t];
                
                i.R[t+1] = i.R[t] + inv_ell*i.I[t];
                
                for(int jx = 0; jx < zones.length; jx++)
                {
                    if(ix != jx)
                    {
                        Zone j = zones[jx];

                        i.S[t+1] += matrix[jx][ix].getMu(t) * j.S[t] - matrix[ix][jx].getMu(t) * i.S[t];

                        i.E[t+1] += matrix[jx][ix].getMu(t) * j.E[t] - matrix[ix][jx].getMu(t) * i.E[t];

                        i.I[t+1] += xi*(matrix[jx][ix].getMu(t) * j.I[t] - matrix[ix][jx].getMu(t) * i.I[t]);

                        i.R[t+1] += matrix[jx][ix].getMu(t) * j.R[t] - matrix[ix][jx].getMu(t) * i.R[t];
                    }
                }
            }
        }
        
        double output = 0.0;
        
        for(int t = 0; t < T; t++)
        {
            int pi_lambda = index_lambda(t);
            
            for(Zone i : zones)
            {
                double temp = i.I[t] - i.lambda[pi_lambda] * i.reportedI[t];
                output += temp*temp;
            }
        }
        
        return output;
    }
    
    
    public double calculateSEIRsearch(double step, int iter)
    {
        for(Zone i : zones)
        {
            i.I[0] = Math.max(1, i.lambda[0] - step*i.gradient_lambda[0]) * i.reportedI[0];
            i.E[0] = Math.min(i.getN()/100, Math.max(0, i.E0 - step * i.gradient_E0));
            
            
            i.R[0] = 0;
            i.S[0] = i.getN() - i.I[0] - i.R[0] - i.E[0];
            

            
        }
        
        for(int t = 0; t < T-1; t++)
        {
            int pi_r = index_r(t);
            
            for(int ix = 0; ix < zones.length; ix++)
            {
                Zone i = zones[ix];
                
                double fSE = Math.min(i.S[t], Math.min(20, Math.max(0, i.r[pi_r] - step*i.gradient_r[pi_r])) * i.S[t] * i.I[t]/i.getN(t));

                i.S[t+1] = i.S[t] - fSE ;
                
                i.E[t+1] = i.E[t] + fSE - (inv_sigma - step*gradient_inv_sigma) *i.E[t];
                
                i.I[t+1] = i.I[t] + (inv_sigma - step*gradient_inv_sigma)*i.E[t] - (inv_ell - step*gradient_inv_ell)*i.I[t];
                
                i.R[t+1] = i.R[t] + (inv_ell - step*gradient_inv_ell)*i.I[t];
                
                /*
                if(iter == 2)
                {
                    System.out.println(i.getId()+" "+t+"\t"+i.S[t]+" "+i.E[t]+" "+i.I[t]+" "+i.R[t]+" "+i.getN()+" "+i.getN(t)+
                            " "+Math.max(0, i.r[pi_r] - step*i.gradient_r[pi_r]));
                }
                */
                
                for(int jx = 0; jx < zones.length; jx++)
                {
                    if(ix != jx)
                    {
                        Zone j = zones[jx];

                        i.S[t+1] += matrix[jx][ix].getMu(t) * j.S[t] - matrix[ix][jx].getMu(t) * i.S[t];

                        i.E[t+1] += matrix[jx][ix].getMu(t) * j.E[t] - matrix[ix][jx].getMu(t) * i.E[t];

                        i.I[t+1] += (xi-step*gradient_xi)*(matrix[jx][ix].getMu(t) * j.I[t] - matrix[ix][jx].getMu(t) * i.I[t]);

                        i.R[t+1] += matrix[jx][ix].getMu(t) * j.R[t] - matrix[ix][jx].getMu(t) * i.R[t];
                    }
                }

            }
        }
        
        double output = 0.0;
        
        for(int t = 0; t < T; t++)
        {
            int pi_lambda = index_lambda(t);
            
            for(Zone i : zones)
            {
                double temp = i.I[t] - Math.max(1, i.lambda[pi_lambda] - step*i.gradient_lambda[pi_lambda]) * i.reportedI[t];
                output += temp*temp;
            }
        }

        
        return output;
    }
}
