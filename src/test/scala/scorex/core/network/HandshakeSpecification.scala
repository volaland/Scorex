package scorex.core.network

import java.net.InetSocketAddress

import org.scalacheck.Gen
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.ObjectGenerators
import scorex.core.app.Version
import scorex.core.network.message.HandshakeSpec


class HandshakeSpecification extends PropSpec
  with PropertyChecks
  with GeneratorDrivenPropertyChecks
  with Matchers
  with ObjectGenerators {

  private val featSerializers = Map(FullNodePeerFeature.featureId -> FullNodePeerFeature.serializer)
  private val noSerializers: PeerFeature.Serializers = Map()

  property("handshake should remain the same after serialization/deserialization") {
    val serializersGen = Gen.oneOf(featSerializers, noSerializers)
    val featuresGen: Gen[Seq[PeerFeature]] = Gen.oneOf(Seq(FullNodePeerFeature), Seq[PeerFeature]())

    forAll(Gen.alphaStr, appVersionGen, Gen.alphaStr, Gen.option(inetSocketAddressGen), Gen.posNum[Long], serializersGen) {
      (appName: String,
       av: Version,
       nodeName: String,
       isaOpt: Option[InetSocketAddress],
       time: Long,
       serializers: PeerFeature.Serializers) =>

        whenever(appName.nonEmpty) {
          val feats: Seq[PeerFeature] = featuresGen.sample.getOrElse(Seq())

          val handshakeSerializer = new HandshakeSpec(serializers, Int.MaxValue)

          val h = Handshake(appName, av, nodeName, isaOpt, feats, time)
          val hr = handshakeSerializer.parseBytes(handshakeSerializer.toBytes(h))
          hr.peerSpec.agentName should be(h.peerSpec.agentName)
          hr.peerSpec.protocolVersion should be(h.peerSpec.protocolVersion)
          hr.peerSpec.declaredAddress should be(h.peerSpec.declaredAddress)
          if (serializers.nonEmpty) hr.peerSpec.features shouldBe h.peerSpec.features else hr.peerSpec.features.isEmpty shouldBe true
          hr.time should be(h.time)
        }
    }
  }
}