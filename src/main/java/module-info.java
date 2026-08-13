module mx.edu.utch.melo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.net.http;
    requires java.desktop;
    requires java.logging;
    requires mysql.connector.j;
    requires com.google.gson;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;

    opens mx.edu.utch.melo.controller to javafx.fxml;

    exports mx.edu.utch.melo;
    exports mx.edu.utch.melo.nav;
    exports mx.edu.utch.melo.model;
    exports mx.edu.utch.melo.util;
    exports mx.edu.utch.melo.validation;
    exports mx.edu.utch.melo.view;
    exports mx.edu.utch.melo.controller;
    exports mx.edu.utch.melo.db;
    exports mx.edu.utch.melo.dao;
    exports mx.edu.utch.melo.dao.impl;
    exports mx.edu.utch.melo.config;
    exports mx.edu.utch.melo.geo;
    exports mx.edu.utch.melo.sesion;
    exports mx.edu.utch.melo.service;
    exports mx.edu.utch.melo.app;
    exports mx.edu.utch.melo.async;
}
