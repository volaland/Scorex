package examples.tailchain.simulation

import examples.commons.SimpleBoxTransaction
import examples.curvepos.transaction.{PublicKey25519NoncedBox, PublicKey25519NoncedBoxSerializer}
import examples.tailchain.modifiers.{TModifier, UtxoSnapshot}
import examples.tailchain.utxo.{AuthenticatedUtxo, PersistentAuthenticatedUtxo}
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.core.transaction.state.MinimalState.VersionTag
import scorex.core.transaction.state.{Insertion, Removal, StateChanges}
import scorex.core.transaction.state.authenticated.BoxMinimalState
import scorex.core.utils.ScorexLogging
import scorex.crypto.authds.avltree.batch.{Insert, Lookup, Remove}

import scala.util.Try
import examples.tailchain.utxo.PersistentAuthenticatedUtxo.ProverType

/**
  * Only for simulations where chain grows strictly linearly. No rollback support.
  */
case class InMemoryAuthenticatedUtxo(size: Int, proverOpt: Option[ProverType], override val version: VersionTag)
  extends
    BoxMinimalState[PublicKey25519Proposition,
      PublicKey25519NoncedBox,
      SimpleBoxTransaction,
      TModifier,
      InMemoryAuthenticatedUtxo] with AuthenticatedUtxo with ScorexLogging {

  import PublicKey25519NoncedBox.{BoxKeyLength, BoxLength}

  assert(size >= 0)

  override lazy val prover = proverOpt.getOrElse {
    val p = new ProverType(keyLength = BoxKeyLength, valueLength = BoxLength) //todo: feed it with genesis state
    log.debug("Starting building a tree for UTXO set")

    //todo: init?

    log.debug("Finished building a tree for UTXO set")
    p
  }

  lazy val rootHash: Array[Byte] = prover.digest

  override type NVCT = InMemoryAuthenticatedUtxo

  override def semanticValidity(tx: SimpleBoxTransaction): Try[Unit] = PersistentAuthenticatedUtxo.semanticValidity(tx)

  override def closedBox(boxId: Array[Byte]): Option[PublicKey25519NoncedBox] =
    proverOpt.flatMap(_.unauthenticatedLookup(boxId).flatMap { bs =>
      PublicKey25519NoncedBoxSerializer.parseBytes(bs).toOption
    })

  //there's no easy way to know boxes associated with a proposition, without an additional index
  override def boxesOf(proposition: PublicKey25519Proposition): Seq[PublicKey25519NoncedBox] = ???

  override def changes(mod: TModifier): Try[StateChanges[PublicKey25519Proposition, PublicKey25519NoncedBox]] =
    PersistentAuthenticatedUtxo.changes(mod)

  //Validate transactions in block and generator box
  override def validate(mod: TModifier): Try[Unit] = Try {
    //    assert(mod.parentId.sameElements(version))  todo: fix & uncomment
    mod match {
      case u: UtxoSnapshot => if (!this.isEmpty) throw new Exception("Utxo Set already imported")
      case _ =>
    }
    super.validate(mod).get
  }

  //todo: newVersion is not used
  override def applyChanges(changes: StateChanges[PublicKey25519Proposition, PublicKey25519NoncedBox],
                            newVersion: VersionTag): Try[InMemoryAuthenticatedUtxo] = Try {

    changes.operations foreach { op =>
      op match {
        case Insertion(b) =>
          prover.performOneOperation(Insert(b.id, b.bytes))
        case Removal(bid) =>
          prover.performOneOperation(Remove(bid))
      }
    }

    prover.generateProof()
    val newVersion = rootHash

    InMemoryAuthenticatedUtxo(size + changes.toAppend.size - changes.toRemove.size, Some(prover), newVersion)
  }

  override def rollbackTo(version: VersionTag): Try[InMemoryAuthenticatedUtxo] = ???

  def isEmpty: Boolean = size == 0
}