package com.curso.webflux.models.dao;

import com.curso.webflux.models.documents.Producto;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

//Con mongodb al igual que con cualquier otra base de datos se puede trabajar con Spring Data JPA y sus herramientas de CrudRepository
//Por defecto cualquier repository de Spring es un componente manejado por el framework asi que no es necesario anotarlo
public interface ProductoDao extends ReactiveMongoRepository<Producto, String> {

}
