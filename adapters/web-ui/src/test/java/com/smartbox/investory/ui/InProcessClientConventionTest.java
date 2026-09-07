package com.smartbox.investory.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@DisplayName("In-process UI client conventions")
class InProcessClientConventionTest {
  private static final List<Class<?>> CLIENTS = discoverClients();

  private static List<Class<?>> discoverClients() {
    var imported =
        new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.smartbox.investory.ui")
                .stream()
                .toList();
    var clients = new ArrayList<Class<?>>();
    for (var javaClass : imported) {
      if (javaClass.getSimpleName().startsWith("InProcess")
          && javaClass.getSimpleName().endsWith("Client")) {
        clients.add(load(javaClass.getFullName()));
      }
    }
    return clients;
  }

  private static Class<?> load(String name) {
    try {
      return InProcessClientConventionTest.class.getClassLoader().loadClass(name);
    } catch (ClassNotFoundException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @Test
  @DisplayName("clients are components backed by explicitly qualified public APIs")
  void clientsAreComponentsBackedByExplicitlyQualifiedPublicApis() {
    for (Class<?> client : CLIENTS) {
      assertNotNull(client.getAnnotation(Component.class), client.getName());
      var constructors = client.getDeclaredConstructors();
      assertTrue(constructors.length == 1, client.getName());
      var parameters = constructors[0].getParameters();
      assertTrue(parameters.length > 0, client.getName());
      for (var parameter : parameters) {
        assertNotNull(parameter.getAnnotation(Qualifier.class), client.getName());
        assertFalse(parameter.getType().getPackageName().contains(".web"), client.getName());
      }
    }
  }
}
