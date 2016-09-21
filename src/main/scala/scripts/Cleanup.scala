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
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.Snapshot
import com.amazonaws.services.ec2.model.Volume
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription

/**
  * Delete all of the ASGs, ELBs, and SGs for a given region.
  */
object Cleanup {

  import scala.collection.JavaConverters._

  private val creds = new DefaultAWSCredentialsProviderChain

  private val ec2Client = new AmazonEC2Client(creds)
  private val asgClient = new AmazonAutoScalingClient(creds)
  private val elbClient = new AmazonElasticLoadBalancingClient(creds)

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

  private def deleteSg(group: String): Boolean = {
    try {
      val req = new DeleteSecurityGroupRequest().withGroupId(group)
      val res = ec2Client.deleteSecurityGroup(req)
      println(s"delete $group: $res")
      true
    } catch {
      case e: Exception =>
        //e.printStackTrace()
        false
    }
  }

  private def deleteSgs(groups: List[String]): Unit = {
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
      .map(_.getGroupId)
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
    deleteSgs(region)
    deleteVolumes(region)
    //deleteSnapshots(region)
  }

  def main(args: Array[String]): Unit = {
    run("us-west-1")
  }
}
