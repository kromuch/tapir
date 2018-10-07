import shapeless.{::, HList, HNil, Path}
import shapeless.ops.function
import shapeless.ops.hlist.Prepend

import scala.annotation.implicitNotFound

package object sapi {
  /*
    Goals:
    - user-friendly types (also in idea); as simple as possible to generate the client, server & docs
    - Swagger-first
    - reasonably type-safe: only as much as needed to gen a server/client/docs, no more
    - programmer friendly (ctrl-space)
   */

  /*
  Akka http directives:
  - authenticate basic/oauth, authorize (fn)
  - cache responses
  - complete (with)
  - decompress request with
  - add/remove cookie
  - extract headers
  - extract body: entity, form field; save to file
  - method matchers
  - complete with file/directory
  - transform request or response (fn)
  - extract parameters
  - match path (extract suffix, ignore trailing slash)
  - redirects
   */

  // define model using case classes
  // capture path components and their mapping to parameters
  // capture query, body, cookie, header parameters w/ mappings
  // read a yaml to get the model / auto-generate the model from a yaml ?
  //   -> only generation possible, due to type-safety
  //   -> the scala model is richer, as it has all the types + case classes
  // server: generate an http4s/akka endpoint matcher
  // client: generate an sttp request definition

  // separate logic from endpoint definition & documentation

  // provide as much or as little detail as needed: optional query param/endpoint desc, samples
  // reasonably type-safe

  // https://github.com/felixbr/swagger-blocks-scala
  // https://typelevel.org/blog/2018/06/15/typedapi.html (https://github.com/pheymann/typedapi)
  // http://fintrospect.io/defining-routes
  // https://github.com/http4s/rho
  // https://github.com/TinkoffCreditSystems/typed-schema

  // what to capture: path, query parameters, body, headers, default response body, error response body

  // streaming?

  // type: string, format: base64, binary, email, ... - use tagged string types ?
  // type: object                                     - implicit EndpointInputType values
  // form fields, multipart uploads, ...

  // extend the path for an endpoint?
  //
  // types, that you are not afraid to write down

  //

  type Id[X] = X
  type Empty[X] = None.type

  @implicitNotFound("???")
  type IsId[U[_]] = U[Unit] =:= Id[Unit]

  sealed trait EndpointInput[I <: HList] {
    def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ]
    def /[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ] = and(other)
  }

  object EndpointInput {
    sealed trait Single[I <: HList] extends EndpointInput[I] {
      def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput[IJ] =
        other match {
          case s: Single[_]     => EndpointInput.Multiple(Vector(this, s))
          case Multiple(inputs) => EndpointInput.Multiple(this +: inputs)
        }
    }

    case class PathSegment(s: String) extends Path[HNil] with Single[HNil]
    case class PathCapture[T](m: TypeMapper[T]) extends Path[T :: HNil] with Single[T :: HNil]

    case class Query[T](name: String, m: TypeMapper[T]) extends Single[T :: HNil]

    case class Multiple[I <: HList](inputs: Vector[Single[_]]) extends EndpointInput[I] {
      override def and[J <: HList, IJ <: HList](other: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): EndpointInput.Multiple[IJ] =
        other match {
          case s: Single[_] => EndpointInput.Multiple(inputs :+ s)
          case Multiple(m)  => EndpointInput.Multiple(inputs ++ m)
        }
    }
  }

  def pathCapture[T: TypeMapper]: EndpointInput[T :: HNil] = EndpointInput.PathCapture(implicitly[TypeMapper[T]])
  implicit def stringToPath(s: String): EndpointInput[HNil] = EndpointInput.PathSegment(s)

  def query[T: TypeMapper](name: String): EndpointInput[T :: HNil] = EndpointInput.Query(name, implicitly[TypeMapper[T]])

  case class Endpoint[U[_], I <: HList](name: Option[String], method: U[Method], input: EndpointInput.Multiple[I]) {
    def name(s: String): Endpoint[U, I] = this.copy(name = Some(s))

    def get(): Endpoint[Id, I] = this.copy[Id, I](method = Method.GET)

    def in[J <: HList, IJ <: HList](i: EndpointInput[J])(implicit ts: Prepend.Aux[I, J, IJ]): Endpoint[U, IJ] =
      this.copy[U, IJ](input = input.and(i))
  }
}