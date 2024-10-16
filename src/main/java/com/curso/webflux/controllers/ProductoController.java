package com.curso.webflux.controllers;

import com.curso.webflux.models.dao.ProductoDao;
import com.curso.webflux.models.documents.Producto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Controller
public class ProductoController {

    @Autowired
    private ProductoDao dao;

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    //Se renderiza la vista listar.html con los productos que se encuentran en la base de datos
    @GetMapping({"/listar", "/"})
    public String listar(Model model){
        Flux<Producto> productos = dao.findAll().map(producto -> {
            producto.setNombre(producto.getNombre().toUpperCase());
            return producto;
        });

        productos.subscribe(prod -> log.info(prod.getNombre()));

        model.addAttribute("productos", productos);   //Aqui aunque no lo vemos se esta haciendo un subscribe ya que la plantilla es la que se subscribe al observable
        model.addAttribute("titulo", "Listado de productos");
        return "listar";
    }

    /*
      A esto se le llama reactive data driver y es una forma de trabajar la contrapresion con thymeleaf.
      Es una de las formas mas potentes y recomendadas para trabajar la contrapresion cuando tenemos algún tipo de delay o recibimos una gran cantidad de elementos.
      Para el reactive data driver el tamaño del buffer se basa en la cantidad de elementos y no en los bytes.
    */
    @GetMapping("/listar-datadriver")
    public String listarDataDriver(Model model){
        Flux<Producto> productos = dao.findAll().map(producto -> {
            producto.setNombre(producto.getNombre().toUpperCase());
            return producto;
        }).delayElements(Duration.ofSeconds(1));  //El lunes hacer pruebas quitando el reactive data driver!!!!!!!!!!!!!!!!

        productos.subscribe(prod -> log.info(prod.getNombre()));

        model.addAttribute("productos", new ReactiveDataDriverContextVariable(productos, 2)); //ReactiveDataDriverContextVariable es lo que permite que se muestren los elementos de a poco (En este caso de 2 en 2)
        model.addAttribute("titulo", "Listado de productos");
        return "listar";
    }

    /*
    Otra forma de trabajar la contrapresion es con el modo chunked, en este modo se establece un limite para el tamaño
    del buffer en bytes.
    El modo chunked se activa cuando configuramos el tamaño del chunk en el application.properties.
    Es recomendable si tenemos flujos con mas de 1000 elementos
    TTFB(Timte to first byte. Tambien aparece como Waiting for server response) significa cuanto tiempo tiene que esperar
    el navegador antes de recibir el primer byte desde el servidor. Es el tiempo que tarda en llegar el primer byte de la
    respuesta y nos sirve para medir desde la consola del navegador la velocidad con la que se procesan los bytes segun
    el tamano del chunk.
     */
    @GetMapping({"/listar-full"})
    public String listarFull(Model model){
        Flux<Producto> productos = dao.findAll().map(producto -> {
            producto.setNombre(producto.getNombre().toUpperCase());
            return producto;
        }).repeat(5000);

        model.addAttribute("productos", productos);
        model.addAttribute("titulo", "Listado de productos");
        return "listar";
    }

    /*
    Nota: Configurando unicamente spring.thymeleaf.reactive.max-chunk-size se establece el tamaño del chunk para todas
    las vistas pero añadiedo ademas chunked-mode-view-names, se establece el tamaño del chunk para las vistas especificadas.

    En este caso se creo una vista nueva llamada listar-chunked.html donde se renderizara el contenido de este flujo.
     */
    @GetMapping({"/listar-chunked"})
    public String listarChunked(Model model){
        Flux<Producto> productos = dao.findAll().map(producto -> {
            producto.setNombre(producto.getNombre().toUpperCase());
            return producto;
        }).repeat(5000);

        model.addAttribute("productos", productos);
        model.addAttribute("titulo", "Listado de productos");
        return "listar-chunked";
    }

}
