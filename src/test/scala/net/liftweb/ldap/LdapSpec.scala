/*
 * Copyright 2010 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.liftweb.ldap

import javax.naming.CommunicationException

import org.apache.mina.util.AvailablePortFinder
import org.specs2.{Specification, control, mutable, specification}

object LdapParamsTest extends Specification {
  private val LDAP = SimpleLDAPVendor

  def is = sequential ^
    s2"""
        This is specification to check parameters processing of LDAPVendor
          handle from map  $map
          handle from file $file
      """

  def map = {
    val testHost = "ldap://localhost:8080"
    LDAP.parameters = () => Map(LDAP.KEY_URL -> testHost)
    LDAP.parameters().get(LDAP.KEY_URL) must beSome(testHost)
  }

  def file = {
    LDAP.parameters = () => LDAP.parametersFromFile("src/test/resources/ldap.properties")
    LDAP.parameters().get(LDAP.KEY_URL) must beSome("ldap://localhost:8000")
  }
}

object LdapVendorTest extends mutable.Specification with specification.BeforeAfterAll with control.Debug {
  sequential

  private var embeddedADS = null: EmbeddedADS

  private val ROOT_DN = "dc=liftweb,dc=net"

  object myLdap extends LDAPVendor

  override def beforeAll() {
    val servicePort = AvailablePortFinder.getNextAvailable(8000)

    myLdap.configure(Map(
      "ldap.url"  -> s"ldap://localhost:$servicePort/",
      "ldap.base" -> ROOT_DN
    ))

    embeddedADS = new EmbeddedADS(ROOT_DN)
    embeddedADS.initServer(servicePort)
  }

  override def afterAll() {
    embeddedADS.stopServer()
  }

  "LDAPVendor" should {
    "handle simple lookups" in {
      myLdap.search("objectClass=person") must contain("cn=TestUser")
    }

    "handle simple authentication" in {
      myLdap.bindUser("cn=TestUser", "letmein")
    }

    "attempt reconnects" in {
      object badLdap extends LDAPVendor
      badLdap.configure()

      // Make sure that we use a port where LDAP won't live
      badLdap.ldapUrl.doWith("ldap://localhost:2") {
        // Let's not make this spec *too* slow
        badLdap.retryInterval.doWith(1000) {
          badLdap.search("objectClass=person") must throwA[CommunicationException]
        }
      }
    }
  }
}