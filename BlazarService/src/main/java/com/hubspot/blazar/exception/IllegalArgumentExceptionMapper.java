package com.hubspot.blazar.exception;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Singleton
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

  @Inject
  public IllegalArgumentExceptionMapper() {}

  @Override
  public Response toResponse(IllegalArgumentException exception) {
    return Response.status(Status.BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity(exception.getMessage())
        .build();
  }
}
