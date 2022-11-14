package org.cloudsimplus.examples.autoscaling;

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.resources.Processor;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelStochastic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.autoscaling.resources.ResourceScaling;
import org.cloudsimplus.autoscaling.resources.ResourceScalingGradual;
import org.cloudsimplus.autoscaling.resources.ResourceScalingInstantaneous;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;

public class Vertical_scaling {

    private static final int SCHEDULING_INTERVAL = 1;
    private static final int HOSTS = 1;

    private static final int HOST_PES = 64;
    private static final int VMS = 2;
    private static final int VM_PES = 14;
    private static final int VM_RAM = 1200;
    private static final int VM_MIPS = 1000;
    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    private static final int CLOUDLETS = 50;

    private static final int CLOUDLET_LEN_BASE = 10_000;

    private int createsVms;

    public static void main(String[] args) {
        new Vertical_scaling();
    }


    private Vertical_scaling() {

        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::onClockTickListener);

        createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletListsWithDifferentDelays();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }


    private void onClockTickListener(EventInfo evt) {
        vmList.forEach(vm ->
                System.out.printf(
                        "\t\tTime %6.1f: Vm %d CPU Usage: %6.2f%% (%2d vCPUs. Running Cloudlets: #%d). RAM usage: %.2f%% (%d MB)%n",
                        evt.getTime(), vm.getId(), vm.getCpuPercentUtilization()*100.0, vm.getNumberOfPes(),
                        vm.getCloudletScheduler().getCloudletExecList().size(),
                        vm.getRam().getPercentUtilization()*100, vm.getRam().getAllocatedResource())
        );
    }

    private void printSimulationResults() {
        final var finishedCloudletList = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getExecStartTime);
        finishedCloudletList.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudletList).build();
    }

    private void createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

        final var dc0 = new DatacenterSimple(simulation, hostList);
        dc0.setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 20000; //in Megabytes
        final long bw = 100000; //in Megabytes
        final long storage = 10000000; //in Megabytes
        return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }

    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final var newVmList = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final var vm = createVm();
            vm.setPeVerticalScaling(createVerticalPeScaling());
            newVmList.add(vm);
        }

        return newVmList;
    }

    private Vm createVm() {
        final int id = createsVms++;

        return new VmSimple(id, VM_MIPS, VM_PES).setRam(VM_RAM).setBw(1000).setSize(10000);
    }

    private VerticalVmScaling createVerticalPeScaling() {
        //The percentage in which the number of PEs has to be scaled
        final double scalingFactor = 0.1;
        final var verticalCpuScaling = new VerticalVmScalingSimple(Processor.class, scalingFactor);


        final double multiplier = 2;
        verticalCpuScaling.setResourceScaling(vs -> multiplier * vs.getScalingFactor() * vs.getAllocatedResource());

        verticalCpuScaling.setLowerThresholdFunction(this::lowerCpuUtilizationThreshold);
        verticalCpuScaling.setUpperThresholdFunction(this::upperCpuUtilizationThreshold);

        return verticalCpuScaling;
    }

    private double lowerCpuUtilizationThreshold(Vm vm) {
        return 0.7;
    }


    private double upperCpuUtilizationThreshold(Vm vm) {
        return 0.8;
    }

    private void createCloudletListsWithDifferentDelays() {
        final int pesNumber = 2;
        final long cloudlets = Math.round(CLOUDLETS * 1.5);
        for (int i = 1; i <= cloudlets; i++) {
            final int delay = i * 2;
            final int length = CLOUDLET_LEN_BASE * i;
            cloudletList.add(createCloudlet(length, pesNumber, delay));
        }
    }


    private Cloudlet createCloudlet(final long length, final int pesNumber) {
        return createCloudlet(length, pesNumber, 0);
    }


    private Cloudlet createCloudlet(final long length, final int pesNumber, final double delay) {

        final var utilizationCpu = new UtilizationModelFull();

        final var utilizationModelDynamic = new UtilizationModelDynamic(0.01);
        final var cl = new CloudletSimple(length, pesNumber);
        cl
                .setUtilizationModelBw(utilizationModelDynamic)
                .setUtilizationModelRam(utilizationModelDynamic)
                .setUtilizationModelCpu(utilizationCpu)
                .setSubmissionDelay(delay);
        return cl;
    }
}
