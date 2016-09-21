
lazy val `aws-scripts` = project.in(file("."))
  .settings(libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-java-sdk-autoscaling" % "1.11.18",
    "com.amazonaws" % "aws-java-sdk-ec2" % "1.11.18",
    "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % "1.11.18",
    "com.amazonaws" % "aws-java-sdk-sts" % "1.11.18"
  ))

