package org.cloudbus.cloudsim.examples;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class premierExemple {
    // la liste de cloudlets
    private static List<Cloudlet> cloudletList;

    /** The vmlists. */
    private static List<Vm> vmlist;
    private static int NbCloudlets =19;
    public static void main(String[] args) {

        Log.printLine("----------------------------Starting premierExemple..----------------------");
        Log.printLine("Starting premierExemple..");
        try {

            // Etape 1: Initialisation des paramètres de la simulation.
            // Doit être faite avant la création des composantes du scénarios.
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // Etape 2: Création de ou des centres de données
            Datacenter datacenter0 = createDatacenter("Datacenter_0");

            //Étape 3: Création du ou des brokers (un broker par client cloud)
            DatacenterBroker broker = createBroker(0);

            int brokerId = broker.getId();

            //Étape 4: création des VMs pour chacun des clients cloud
            // la première est associé au client 1 et la deuxième au client 2
            vmlist = new ArrayList<Vm>();

            vmlist = createVM(brokerId, 7, 1);


            //soumettre les listes de VMs aux brokers
            broker.submitVmList(vmlist);


            //Étape 5: Création des cloudlets (tâches à exécuter sur les VMs
            cloudletList = createCloudlet(brokerId, NbCloudlets, 1); // creating 15 cloudlets

            //soumettre les listes de cloudlets aux brokers
            broker.submitCloudletList(cloudletList);

            // Étape 6: lancer la simulation
            CloudSim.startSimulation();

            // Imprimer les résultats de la simulation
            List<Cloudlet> newList1 = broker.getCloudletReceivedList();

            CloudSim.stopSimulation();

            Log.print("=============> User "+brokerId+"    ");
            printCloudletList(newList1);

            Log.printLine("Simulation finished!");
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
        }
    }

    private static Datacenter createDatacenter(String name){

        // création des serveurs ou hôtes
        List<Host> hostList = new ArrayList<Host>();

        // création de la liste des coeurs CPU (le nombre d'élements est le nombre de coeur CPU détenus par l'hôte)
        List<Pe> peList = new ArrayList<Pe>();

        // int mips=1000; //puissance de CPU en termes de million d'instructions par seconde

        // mon code
        int mips=2000;

        peList.add(new Pe(0, new PeProvisionerSimple(mips))); // ID du coeur CPU (Pe pour processing element) et mips
        peList.add(new Pe(1, new PeProvisionerSimple(mips)));
        peList.add(new Pe(2, new PeProvisionerSimple(mips)));
        peList.add(new Pe(3, new PeProvisionerSimple(mips)));
        //peList.add(new Pe(4, new PeProvisionerSimple(mips)));


        //Création des hôtes (serveurs) et leur ajout dans la liste hostList

        int ram = 4096;
        long storage = 1000000;
        int bw = 10000;

        for(int hostId=0;hostId<5;hostId++){
            hostList.add(
                    new Host(
                            hostId,
                            new RamProvisionerSimple(ram),
                            new BwProvisionerSimple(bw),
                            storage,
                            peList,
                            new VmSchedulerTimeShared(peList)
                            //new VmSchedulerSpaceShared(peList)
                    )
            );
        }

        // Création du centre de données avec les caractéristiques suivantes
        String arch = "x86";            // system architecture
        String os = "Linux";            // operating system
        String vmm = "Xen";             //virtualization monitor
        double time_zone = 10.0;        // time zone this resource located
        double cost = 3.0;              // the cost of using processing in this resource
        double costPerMem = 0.05;		// the cost of using memory in this resource
        double costPerStorage = 0.001;	// the cost of using storage in this resource
        double costPerBw = 0.0;			// the cost of using bw in this resource

        LinkedList<Storage> storageList = new LinkedList<Storage>();


        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics,
                    new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    private static DatacenterBroker createBroker(int id){

        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker"+id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    private static List<Vm> createVM(int userId, int vms, int idShift) {
        LinkedList<Vm> list = new LinkedList<Vm>();

        long size = 10000;
        int ram = 1024;
        // int mips = 250;
        int mips = 1000;
        long bw = 1000;
        int pesNumber = 3;
        String vmm = "Xen";

        Vm[] vm = new Vm[vms];

        for(int i=0;i<vms;i++){
            vm[i] = new Vm(idShift + i, userId, mips, pesNumber,
                    ram, bw, size, vmm,
                    new CloudletSchedulerTimeShared()
                    //new CloudletSchedulerSpaceShared()
            );
            list.add(vm[i]);
        }
        return list;
    }


    private static List<Cloudlet> createCloudlet(int userId, int cloudlets, int idShift){
        LinkedList<Cloudlet> list = new LinkedList<Cloudlet>();

        //cloudlet parameters
        long length = 5000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        Cloudlet[] cloudlet = new Cloudlet[cloudlets];

        for(int i=0;i<cloudlets;i++){
            cloudlet[i] = new Cloudlet(idShift + i, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            // setting the owner of these Cloudlets
            cloudlet[i].setUserId(userId);
            list.add(cloudlet[i]);
        }
        return list;
    }

    /**
     * Prints the Cloudlet objects
     * @param list  list of Cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) throws IOException {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";

        Log.printLine();
        Log.printLine("========== OUTPUT Scénario 04 ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getStatus() == Cloudlet.SUCCESS){
                Log.print("SUCCESS");

                Log.printLine( indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime())+
                        indent + indent + dft.format(cloudlet.getFinishTime()));
            }
        }
    }

}