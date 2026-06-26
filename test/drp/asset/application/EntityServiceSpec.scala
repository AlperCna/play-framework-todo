package drp.asset.application

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.domain.AssetDomainError
import drp.asset.infrastructure.InMemoryEntityRepository

class EntityServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private def newService = new EntityServiceImpl(new InMemoryEntityRepository())

  "EntityService.register" should {

    "persist a valid entity and list it" in {
      val service = newService
      service.register("Akbank", "brand").futureValue shouldBe a[Right[_, _]]
      service.list().futureValue.map(_.name) should contain("Akbank")
    }

    "reject a blank name and persist nothing" in {
      val service = newService
      service.register("   ", "brand").futureValue shouldBe Left(AssetDomainError.BlankEntityName)
      service.list().futureValue shouldBe empty
    }

    "allow duplicate entity names" in {
      val service = newService
      service.register("Akbank", "brand").futureValue
      service.register("Akbank", "brand").futureValue shouldBe a[Right[_, _]]
      service.list().futureValue.count(_.name == "Akbank") shouldBe 2
    }
  }
}
