package com.curso.webflux.controllers;

import com.curso.webflux.models.documents.Producto;
import com.curso.webflux.models.services.ProductoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;

@SessionAttributes("producto") //Para que el objeto producto se mantenga en la sesion (Para poder tener presente el id del producto que se esta editando)
@Controller
public class ProductoController {

    @Autowired
    private ProductoService service;

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    //Se renderiza la vista listar.html con los productos que se encuentran en la base de datos
    @GetMapping({"/listar", "/"})
    public Mono<String> listar(Model model){
        Flux<Producto> productos = service.findAllConNombreUpperCase();

        productos.subscribe(prod -> log.info(prod.getNombre()));

        model.addAttribute("productos", productos);   //Aqui aunque no lo vemos se esta haciendo un subscribe ya que la plantilla es la que se subscribe al observable
        model.addAttribute("titulo", "Listado de productos");
        return Mono.just("listar");
    }

    //Metodo que despliega en la vista el formulario para crear un producto
    //Tenemos que pasar el objeto producto a la vista para que se pueda bindear con el formulario
    @GetMapping("/form")
    public Mono<String> crear(Model model){
        model.addAttribute("producto", new Producto());
        model.addAttribute("titulo", "Formulario de producto");
        model.addAttribute("boton", "Crear");
        return Mono.just("form");   //Cuando retornamos el nombre de la vista se carga dicha vista dentro de la peticion http
    }

    //El objeto producto es bidireccional, va del controlador a la vista y de la vista al controlador
    //@Valid es para que se apliquen las validaciones que se encuentran en la clase Producto
    //BindingResult es para poder manejar los errores de validacion y debe ir justo despues del objeto que se esta validando
    //Como el objeto producto ya se esta pasando como parametro no es necesario pasarlo como atributo del model, se motrara en la vista automaticamente, esto es porque la clase se llama igual que el nombre del atributo pasado a model: model.addAttribute("producto", new Producto());
    @PostMapping("/form")
    public Mono<String> guardar(@Valid Producto producto, BindingResult result, Model model, SessionStatus status){ //SessionStatus para limpiar el objeto producto de la sesion
        if (result.hasErrors()){
            model.addAttribute("titulo", "Errores en formulario producto"); //Si hay errores en el formulario se cambia el titulo
            model.addAttribute("boton", "Guardar");
            return Mono.just("form");
        } else {
            status.setComplete(); //Limpiamos el objeto producto de la sesion cuando finaliza el proceso y es guardado en la bd
            if (producto.getCreateAt() == null){
                producto.setCreateAt(new Date());
            }
            return service.save(producto).doOnNext(p -> {
                log.info("Producto guardado: " + p.getNombre() + " Id: " + p.getId());
            }).thenReturn("redirect:/listar?success=producto+guardado+con+exito"); //El string redirige a la vista listar.html, NO es que cargue la vista sino que redirige a la url
        }
    }

    //Otra version mas reactiva del metodo editar pero con un inconveniente, no se puede bindear el objeto producto con el formulario
    @GetMapping("/form-v2/{id}")
    public Mono<String> editarV2(@PathVariable String id, Model model){

        return service.findById(id).doOnNext(p -> {
            log.info("Producto recuperado: " + p.getNombre());
            model.addAttribute("titulo", "Editar producto");
            model.addAttribute("boton", "Editar");
            model.addAttribute("producto", p);
        }).defaultIfEmpty(new Producto())
                .flatMap(p -> {
                    if(p.getId() == null){
                        return Mono.error(new InterruptedException("No existe el producto a editar"));
                    }
                    return Mono.just(p);
                })
                .then(Mono.just("form"))
                .onErrorResume(ex -> Mono.just("redirect:/listar?error=no+existe+el+producto+a+editar"));


    }

    @GetMapping("/form/{id}")
    public Mono<String> editar(@PathVariable String id, Model model){ //Model para poder ir a buscar el producto por id y pasarlo a la vista
        Mono<Producto> productoMono = service.findById(id).doOnNext(p -> log.info("Producto recuperado: " + p.getNombre())).defaultIfEmpty(new Producto()); //Si no se encuentra el producto se crea un producto vacio

        model.addAttribute("boton", "Editar");
        model.addAttribute("titulo", "Editar producto");
        model.addAttribute("producto", productoMono);

        return Mono.just("form");

    }


    /*
      A esto se le llama reactive data driver y es una forma de trabajar la contrapresion con thymeleaf.
      Es una de las formas mas potentes y recomendadas para trabajar la contrapresion cuando tenemos algún tipo de delay
      o recibimos una gran cantidad de elementos.
      Para el reactive data driver el tamaño del buffer se basa en la cantidad de elementos y no en los bytes.
    */
    @GetMapping("/listar-datadriver")
    public String listarDataDriver(Model model){
        Flux<Producto> productos = service.findAllConNombreUpperCase().delayElements(Duration.ofSeconds(1));  //El lunes hacer pruebas quitando el reactive data driver!!!!!!!!!!!!!!!!

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
        Flux<Producto> productos = service.findAllConNombreUpperCaseRepeat();

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
        Flux<Producto> productos = service.findAllConNombreUpperCaseRepeat();

        model.addAttribute("productos", productos);
        model.addAttribute("titulo", "Listado de productos");
        return "listar-chunked";
    }

}
