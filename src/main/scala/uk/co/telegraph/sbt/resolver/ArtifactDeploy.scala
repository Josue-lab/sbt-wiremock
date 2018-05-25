package uk.co.telegraph.sbt.resolver

import java.util.zip.ZipFile

import sbt._

import scala.concurrent.{Await, Future}
import scala.util._
import scala.sys.process._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait ArtifactDeploy {

  private def validJar(file:File) = Try(new ZipFile(file)).isSuccess

  def apply(
    moduleId:ModuleID,
    targetDir:File,
    downloadIfOrderThan:Duration,
    logger: Logger
  )(implicit scalaVersion:String, repository:Repository):File = {

    def isStale(file:File) = moduleId.revision == LatestVersionLabel && System.currentTimeMillis - file.lastModified > downloadIfOrderThan.toMillis

    val localJar = new File(targetDir, s"${moduleId.name}.jar")

    if(!targetDir.exists()){
      logger.info(s"Creating target directory $targetDir")
      targetDir.mkdirs()
    }

    if(!localJar.exists() || isStale(localJar) || !validJar(localJar) ){
      Try{
        val remoteJar = moduleId.resolveVersion().remoteJar

        logger.info(s"Downloading jar from [$remoteJar] to [${localJar.getAbsolutePath}]")

        val process = ( remoteJar #> localJar ).run()
        Await.result(Future(blocking(process.exitValue())), 500 millis)

      } match {
        case Success(_) => {}
        case Failure(e) => sys.error(s"Fail to download artifact [$moduleId]. Reason: ${e.getMessage}")
      }
    }
    if (!validJar(localJar)) {
      sys.error(s"Invalid jar file at [${localJar.getAbsolutePath}]")
    }
    localJar
  }
}

object ArtifactDeploy extends ArtifactDeploy
