

package org.cloudsimplus.examples.autoscaling;

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.HorizontalVmScalingSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;

public class Horizontal_scaling {
    private static final int SCHEDULING_INTERVAL = 4;


    private static final int CLOUDLETS_CREATION_INTERVAL = SCHEDULING_INTERVAL;

    private static final int HOSTS = 1;
    private static final int HOST_PES = 10;
    private static final int VMS = 2;
    private static final int CLOUDLETS = 30;
    private final CloudSim simulation;
    private final Datacenter dc0;
    private final DatacenterBroker broker0;
    private final List<Host> hostList;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;

    /**
     * Different lengths that will be randomly assigned to created Cloudlets.
     */
    private static final long CLOUDLET_LENGTHS[] = {10000, 20000, 30000,
            40000, 50000, 60000, 70000, 80000, 90000, 100000, 110000,
            120000, 130000, 140000, 150000};

    private final ContinuousDistribution rand;

    private int createdCloudlets;
    private int createsVms;

    public static void main(String[] args) {
        new Horizontal_scaling();
    }


    private Horizontal_scaling() {

        final long seed = 1;
        rand = new UniformDistr(0, CLOUDLET_LENGTHS.length, seed);
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::createNewCloudlets);

        dc0 = createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);


        broker0.setVmDestructionDelay(10.0);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletList();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }

    private void printSimulationResults() {
        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getExecStartTime);
        finishedCloudlets.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudlets).build();
    }

    private void createCloudletList() {
        for (int i = 0; i < CLOUDLETS; i++) {
            cloudletList.add(createCloudlet());
        }
    }

    private void createNewCloudlets(final EventInfo info) {
        final long time = (long) info.getTime();
        if (time % CLOUDLETS_CREATION_INTERVAL == 0 && time <= 5) {
            final int cloudletsNumber = 15;
            System.out.printf("\t#Creating %d Cloudlets at time %d.%n", cloudletsNumber, time);
            final List<Cloudlet> newCloudlets = new ArrayList<>(cloudletsNumber);
            for (int i = 0; i < cloudletsNumber; i++) {
                final Cloudlet cloudlet = createCloudlet();
                cloudletList.add(cloudlet);
                newCloudlets.add(cloudlet);
            }

            broker0.submitCloudletList(newCloudlets);
        }
    }


    private Datacenter createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

        return new DatacenterSimple(simulation, hostList).setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final List<Pe> peList = new ArrayList<>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }

        final long ram = 20000; // in Megabytes
        final long storage = 10000000; // in Megabytes
        final long bw = 10000; //in Megabits/s
        return new HostSimple(ram, bw, storage, peList)
                .setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
    }


    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final List<Vm> newList = new ArrayList<>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final Vm vm = createVm();
            createHorizontalVmScaling(vm);
            newList.add(vm);
        }

        return newList;
    }

    private void createHorizontalVmScaling(final Vm vm) {
        final HorizontalVmScaling horizontalScaling = new HorizontalVmScalingSimple();
        horizontalScaling
                .setVmSupplier(this::createVm)
                .setOverloadPredicate(this::isVmOverloaded);
        vm.setHorizontalScaling(horizontalScaling);
    }

    private boolean isVmOverloaded(final Vm vm) {
        return vm.getCpuPercentUtilization() > 0.7;
    }

    private Vm createVm() {
        final int id = createsVms++;
        return new VmSimple(id, 1000, 2)
                .setRam(1000).setBw(1000).setSize(10000)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createCloudlet() {
        final int id = createdCloudlets++;
        final var utilizadionModelDynamic = new UtilizationModelDynamic(0.7);

        //randomly selects a length for the cloudlet
        final long length = CLOUDLET_LENGTHS[(int) rand.sample()];
        return new CloudletSimple(id, length, 2)
                .setFileSize(1024)
                .setOutputSize(1024)
                .setUtilizationModelBw(utilizadionModelDynamic)
                .setUtilizationModelRam(utilizadionModelDynamic)
                .setUtilizationModelCpu(new UtilizationModelFull());
    }
}

