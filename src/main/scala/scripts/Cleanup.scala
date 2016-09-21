package scripts

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest
import com.amazonaws.services.ec2.model.DeleteVolumeRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest
import com.amazonaws.services.ec2.model.DescribeVolumesRequest
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.Snapshot
import com.amazonaws.services.ec2.model.Volume
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import com.amazonaws.services.elasticmapreduce.model.ClusterState
import com.amazonaws.services.elasticmapreduce.model.ClusterSummary
import com.amazonaws.services.elasticmapreduce.model.ListClustersRequest
import com.amazonaws.services.elasticmapreduce.model.SetTerminationProtectionRequest
import com.amazonaws.services.elasticmapreduce.model.TerminateJobFlowsRequest

/**
  * Delete all of the ASGs, ELBs, and SGs for a given region.
  */
object Cleanup {

  import scala.collection.JavaConverters._

  private val creds = new DefaultAWSCredentialsProviderChain

  private val ec2Client = new AmazonEC2Client(creds)
  private val asgClient = new AmazonAutoScalingClient(creds)
  private val elbClient = new AmazonElasticLoadBalancingClient(creds)
  private val emrClient = new AmazonElasticMapReduceClient(creds)

  private def describeAsgs(req: DescribeAutoScalingGroupsRequest): List[AutoScalingGroup] = {
    val res = asgClient.describeAutoScalingGroups(req)
    val groups = res.getAutoScalingGroups.asScala.toList
    if (res.getNextToken == null)
      groups
    else
      groups ::: describeAsgs(req.withNextToken(res.getNextToken))
  }

  def deleteAsgs(region: String): Unit = {
    asgClient.setEndpoint(s"autoscaling.$region.amazonaws.com")
    describeAsgs(new DescribeAutoScalingGroupsRequest).foreach { asg =>
      val req = new DeleteAutoScalingGroupRequest()
        .withAutoScalingGroupName(asg.getAutoScalingGroupName)
        .withForceDelete(true)
      val res = asgClient.deleteAutoScalingGroup(req)
      println(s"delete ${asg.getAutoScalingGroupName}: $res")
    }
  }

  private def describeLaunchConfigs(req: DescribeLaunchConfigurationsRequest): List[LaunchConfiguration] = {
    val res = asgClient.describeLaunchConfigurations(req)
    val groups = res.getLaunchConfigurations.asScala.toList
    if (res.getNextToken == null)
      groups
    else
      groups ::: describeLaunchConfigs(req.withNextToken(res.getNextToken))
  }

  def deleteLaunchConfigs(region: String): Unit = {
    asgClient.setEndpoint(s"autoscaling.$region.amazonaws.com")
    describeLaunchConfigs(new DescribeLaunchConfigurationsRequest).foreach { lc =>
      val req = new DeleteLaunchConfigurationRequest()
        .withLaunchConfigurationName(lc.getLaunchConfigurationName)
      val res = asgClient.deleteLaunchConfiguration(req)
      println(s"delete ${lc.getLaunchConfigurationName}: $res")
    }
  }

  private def describeElbs(req: DescribeLoadBalancersRequest): List[LoadBalancerDescription] = {
    val res = elbClient.describeLoadBalancers(req)
    val values = res.getLoadBalancerDescriptions.asScala.toList
    if (res.getNextMarker == null)
      values
    else
      values ::: describeElbs(req.withMarker(res.getNextMarker))
  }

  def deleteElbs(region: String): Unit = {
    elbClient.setEndpoint(s"elasticloadbalancing.$region.amazonaws.com")
    describeElbs(new DescribeLoadBalancersRequest).foreach { elb =>
      val req = new DeleteLoadBalancerRequest()
        .withLoadBalancerName(elb.getLoadBalancerName)
      val res = elbClient.deleteLoadBalancer(req)
      println(s"delete ${elb.getLoadBalancerName}: $res")
    }
  }

  private def describeSgs(req: DescribeSecurityGroupsRequest): List[SecurityGroup] = {
    val res = ec2Client.describeSecurityGroups(req)
    res.getSecurityGroups.asScala.toList
  }

  private def deleteSg(group: SecurityGroup): Boolean = {
    try {
      ec2Client.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest()
        .withIpPermissions(group.getIpPermissions))
      val req = new DeleteSecurityGroupRequest().withGroupId(group.getGroupId)
      val res = ec2Client.deleteSecurityGroup(req)
      println(s"delete $group: $res")
      true
    } catch {
      case e: Exception =>
        println(s"WARN: $group: ${e.getMessage}")
        //e.printStackTrace()
        false
    }
  }

  private def deleteSgs(groups: List[SecurityGroup]): Unit = {
    // HACK to work around dependency violations when deleting all groups. Should
    // rewrite this to figure out the tree and delete in the correct order...
    if (groups.nonEmpty) {
      deleteSgs(groups.filter(sg => !deleteSg(sg)))
    }
  }

  def deleteSgs(region: String): Unit = {
    ec2Client.setEndpoint(s"ec2.$region.amazonaws.com")
    val groups = describeSgs(new DescribeSecurityGroupsRequest)
      .filter(_.getGroupName != "default")
    deleteSgs(groups)
  }

  private def describeVolumes(req: DescribeVolumesRequest): List[Volume] = {
    val res = ec2Client.describeVolumes(req)
    val values = res.getVolumes.asScala.toList
    if (res.getNextToken == null)
      values
    else
      values ::: describeVolumes(req.withNextToken(res.getNextToken))
  }

  def deleteVolumes(region: String): Unit = {
    elbClient.setEndpoint(s"ec2.$region.amazonaws.com")
    describeVolumes(new DescribeVolumesRequest).foreach { v =>
      val req = new DeleteVolumeRequest()
        .withVolumeId(v.getVolumeId)
      val res = ec2Client.deleteVolume(req)
      println(s"delete ${v.getVolumeId}: $res")
    }
  }

  private def describeClusters(req: ListClustersRequest): List[ClusterSummary] = {
    req.withClusterStates(
      ClusterState.RUNNING,
      ClusterState.BOOTSTRAPPING,
      ClusterState.STARTING,
      ClusterState.WAITING)
    val res = emrClient.listClusters(req)
    val values = res.getClusters.asScala.toList
    if (res.getMarker == null)
      values
    else
      values ::: describeClusters(req.withMarker(res.getMarker))
  }

  private def deleteClusters(region: String): Unit = {
    emrClient.setEndpoint(s"elasticmapreduce.$region.amazonaws.com")
    describeClusters(new ListClustersRequest).foreach { c =>
      emrClient.setTerminationProtection(new SetTerminationProtectionRequest()
        .withJobFlowIds(c.getId)
        .withTerminationProtected(false))
      val req = new TerminateJobFlowsRequest()
        .withJobFlowIds(c.getId)
      val res = emrClient.terminateJobFlows(req)
      println(s"delete ${c.getId}: $res")
    }
  }

  private def describeSnapshots(req: DescribeSnapshotsRequest): List[Snapshot] = {
    val res = ec2Client.describeSnapshots(req)
    val values = res.getSnapshots.asScala.toList
    if (res.getNextToken == null)
      values
    else
      values ::: describeSnapshots(req.withNextToken(res.getNextToken))
  }

  def deleteSnapshots(region: String): Unit = {
    elbClient.setEndpoint(s"ec2.$region.amazonaws.com")
    describeSnapshots(new DescribeSnapshotsRequest).foreach { v =>
      val req = new DeleteSnapshotRequest()
        .withSnapshotId(v.getSnapshotId)
      val res = ec2Client.deleteSnapshot(req)
      println(s"delete ${v.getSnapshotId}: $res")
    }
  }

  def run(region: String): Unit = {
    deleteAsgs(region)
    deleteLaunchConfigs(region)
    deleteElbs(region)
    deleteClusters(region)
    deleteSgs(region)
    deleteVolumes(region)
    //deleteSnapshots(region)
  }

  def main(args: Array[String]): Unit = {
    run("eu-west-1")
  }
}
