package com.hubspot.blazar.data;

import org.junit.After;
import org.junit.BeforeClass;

import com.google.inject.Guice;
import com.hubspot.blazar.data.service.BlazarTestBase;
import com.hubspot.blazar.data.service.BlazarTestModule;

public class BlazarDataTestBase extends BlazarTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    synchronized (injector) {
      if (injector.get() == null) {
        injector.set(Guice.createInjector(new BlazarTestModule(), new BlazarDataModule()));
        runSql("schema.sql");
      }
    }
  }

  @After
  public void cleanup() throws Exception {
    runSql("schema.sql");
  }

}
