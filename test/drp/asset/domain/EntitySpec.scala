package drp.asset.domain

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.shared.domain.DomainError

class EntitySpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-29T00:00:00Z")

  "Entity.create" should {
    "succeed with a non-blank name and a known type" in {
      Entity.create("Akbank", "brand", now) shouldBe
        Right(Entity(EntityId(0L), "Akbank", EntityType.Brand, now, now))
    }
    "trim the name" in {
      Entity.create("  Akbank  ", "brand", now).map(_.name) shouldBe Right("Akbank")
    }
    "reject a blank name" in {
      Entity.create("   ", "brand", now) shouldBe Left(DomainError.EmptyEntityName)
    }
    "tolerate an unknown type as Other (open enum)" in {
      Entity.create("X", "weird-type", now).map(_.entityType) shouldBe Right(EntityType.Other("weird-type"))
    }
  }

  "Entity.edit" should {
    "change name + type and keep id/createdAt" in {
      val e = Entity.create("Akbank", "brand", now).toOption.get
      val later = now.plusSeconds(60)
      e.edit("Akbank TR", "institution", later) shouldBe
        Right(e.copy(name = "Akbank TR", entityType = EntityType.Institution, updatedAt = later))
    }
    "reject a blank new name" in {
      val e = Entity.create("Akbank", "brand", now).toOption.get
      e.edit("  ", "brand", now) shouldBe Left(DomainError.EmptyEntityName)
    }
  }
}
