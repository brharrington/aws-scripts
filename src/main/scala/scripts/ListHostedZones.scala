package scripts

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.route53.AmazonRoute53Client
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesRequest

// roughly equivalent to
// aws route53 list-hosted-zones --region us-east-1 | jq -r '.HostedZones[] | .Name' | nl
object ListHostedZones {

  import scala.collection.JavaConverters._

  private val creds = new DefaultAWSCredentialsProviderChain

  private val r53Client = new AmazonRoute53Client(creds)

  private def list(req: ListHostedZonesRequest): List[HostedZone] = {
    val res = r53Client.listHostedZones(req)
    val groups = res.getHostedZones.asScala.toList
    if (res.getNextMarker == null)
      groups
    else
      groups ::: list(req.withMarker(res.getNextMarker))
  }

  def run(region: String): Unit = {
    list(new ListHostedZonesRequest).zipWithIndex.foreach { case (z, i) =>
      printf("%5d: %s%n", i, z.getName)
    }
  }

  def main(args: Array[String]): Unit = {
    run("us-east-1")
  }
}
