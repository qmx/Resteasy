package org.jboss.resteasy.test.interceptors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ClientResponseFailure;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.test.BaseResourceTest;
import org.jboss.resteasy.test.TestPortProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.OutputStream;

import static org.jboss.resteasy.test.TestPortProvider.*;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class GzipTest extends BaseResourceTest
{

   @Path("/")
   public static interface IGZIP
   {
      @GET
      @Path("text")
      @Produces("text/plain")
      public String getText();

      @GET
      @Path("encoded/text")
      @GZIP
      public String getGzipText();

      @GET
      @Path("encoded/text/error")
      @GZIP
      public String getGzipErrorText();
   }

   @Path("/")
   public static class GZIPService
   {
      @GET
      @Path("text")
      @Produces("text/plain")
      public Response getText(@Context HttpHeaders headers)
      {
         String acceptEncoding = headers.getRequestHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING);
         System.out.println(acceptEncoding);
         Assert.assertEquals("gzip, deflate", acceptEncoding);
         return Response.ok("HELLO WORLD").header("Content-Encoding", "gzip").build();
      }

      @GET
      @Path("encoded/text")
      @GZIP
      public String getGzipText()
      {
         return "HELLO WORLD";
      }

      @GET
      @Path("error")
      @GZIP
      @Produces({"application/json;charset=UTF-8"})
      public StreamingOutput getTest()
      {
         return new StreamingOutput()
         {
            @Override
            public void write(OutputStream outputStream) throws IOException, WebApplicationException
            {
               throw new WebApplicationException(405);
            }
         };
      }

      @GET
      @Path("encoded/text/error")
      @GZIP
      public String getGzipErrorText()
      {
         throw new WebApplicationException(
                 Response.status(500).entity("Hello").type("text/plain").build()
         );
      }
   }

   @Before
   public void setUp() throws Exception
   {
      addPerRequestResource(GZIPService.class);
   }


   @Test
   public void testProxy() throws Exception
   {
      IGZIP proxy = ProxyFactory.create(IGZIP.class, generateBaseUrl());
      Assert.assertEquals("HELLO WORLD", proxy.getText());
      Assert.assertEquals("HELLO WORLD", proxy.getGzipText());

      // resteasy-651
      try
      {
          String error = proxy.getGzipErrorText();
          Assert.fail("unreachable");
      }
      catch (ClientResponseFailure failure)
      {
         Assert.assertEquals(500, failure.getResponse().getStatus());
         String txt = (String)failure.getResponse().getEntity(String.class);
         Assert.assertEquals("Hello", txt);
      }
   }

   @Test
   public void testRequest() throws Exception
   {
      ClientRequest request = new ClientRequest(TestPortProvider.generateURL("/text"));
      ClientResponse<String> response = request.get(String.class);
      Assert.assertEquals("HELLO WORLD", response.getEntity());

   }

   @Test
   public void testRequestError() throws Exception
   {
      ClientRequest request = new ClientRequest(TestPortProvider.generateURL("/error"));
      ClientResponse<String> response = request.get(String.class);
      Assert.assertEquals(405, response.getStatus());

   }

   @Test
   public void testRequest2() throws Exception
   {
      ClientRequest request = new ClientRequest(TestPortProvider.generateURL("/encoded/text"));
      ClientResponse<String> response = request.get(String.class);
      Assert.assertEquals("HELLO WORLD", response.getEntity());

   }

   @Test
   public void testWasZipped() throws Exception
   {
      // test that it was zipped by running it through Apache HTTP Client which does not automatically unzip

      HttpClient client = new DefaultHttpClient();
      {
         HttpGet get = new HttpGet(TestPortProvider.generateURL("/encoded/text"));
         get.addHeader("Accept-Encoding", "gzip, deflate");
         HttpResponse response = client.execute(get);
         Assert.assertEquals(200, response.getStatusLine().getStatusCode());
         Assert.assertEquals("gzip", response.getFirstHeader("Content-Encoding").getValue());

         // test that it is actually zipped
         String entity = EntityUtils.toString(response.getEntity());
         Assert.assertNotSame(entity, "HELLO WORLD");
      }


      {
         HttpGet get = new HttpGet(TestPortProvider.generateURL("/text"));
         get.addHeader("Accept-Encoding", "gzip, deflate");
         HttpResponse response = client.execute(get);
         Assert.assertEquals(200, response.getStatusLine().getStatusCode());
         Assert.assertEquals("gzip", response.getFirstHeader("Content-Encoding").getValue());

         // test that it is actually zipped
         String entity = EntityUtils.toString(response.getEntity());
         Assert.assertNotSame(entity, "HELLO WORLD");
      }
   }

   @Test
   public void testWithoutAcceptEncoding() throws Exception
   {
      // test that if there is no accept-encoding: gzip header that result isn't encoded

      HttpClient client = new DefaultHttpClient();
      HttpGet get = new HttpGet(TestPortProvider.generateURL("/encoded/text"));
      HttpResponse response = client.execute(get);
      Assert.assertEquals(200, response.getStatusLine().getStatusCode());
      Assert.assertNull(response.getFirstHeader("Content-Encoding"));

      // test that it is actually zipped
      String entity = EntityUtils.toString(response.getEntity());
      Assert.assertEquals(entity, "HELLO WORLD");

   }


}
