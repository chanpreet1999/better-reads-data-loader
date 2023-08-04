package com.codersingh.betterreadsdataloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.codersingh.betterreadsdataloader.author.Author;
import com.codersingh.betterreadsdataloader.author.AuthorRepository;
import com.codersingh.betterreadsdataloader.book.Book;
import com.codersingh.betterreadsdataloader.book.BookRepository;

import connection.DataStaxAstraProperties;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterReadsDataLoaderApplication {

	@Autowired
	AuthorRepository authorRepository;

	@Autowired
	BookRepository bookRepository;

	@Value("${datadump.location.author}")
	private String authorDumpLocation;

	@Value("${datadump.location.works}")
	private String worksDumpLocation;

	private String string;

	public static void main(String[] args) {
		SpringApplication.run(BetterReadsDataLoaderApplication.class, args);
	}

	@PostConstruct
	public void start() {
		System.out.println("Application started");
		// initAuthors();
		initWorks();
		System.out.println(authorDumpLocation);
	}

	private void initAuthors() {
		Path path = Paths.get(authorDumpLocation);
		try (Stream<String> lines = Files.lines(path)) {
			lines.forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					// Construct the author object
					Author author = new Author();
					author.setName(jsonObject.optString("name"));
					author.setPersonalName(jsonObject.optString("personal_name"));
					author.setId(jsonObject.optString("key").replace("/authors/", ""));
					// persisit it in db
					System.out.println("Saving author with author name: " + author.getName() + " ...");
					authorRepository.save(author);
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void initWorks() {
		Path path = Paths.get(worksDumpLocation);
		DateTimeFormatter dateFortmat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		try (Stream<String> lines = Files.lines(path)) {
			lines.forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					// Construct the book object
					Book book = new Book();

					book.setId(jsonObject.getString("key").replace("/works/", ""));
					book.setName(jsonObject.optString("title"));
					JSONObject descriptionObj = jsonObject.optJSONObject("description");
					if (descriptionObj != null) {
						book.setDescription(descriptionObj.optString("value"));
					}

					JSONObject publishedObj = jsonObject.optJSONObject("created");
					if (publishedObj != null) {
						String dateStr = publishedObj.getString("value");
						book.setPublishedDate(LocalDate.parse(dateStr, dateFortmat));
					}

					JSONArray coversJSONArray = jsonObject.optJSONArray("covers");
					if (coversJSONArray != null) {
						List<String> coverIds = new ArrayList<>();
						for (int i = 0; i < coversJSONArray.length(); i++) {
							coverIds.add(coversJSONArray.getString(i));
						}
						book.setCoverIds(coverIds);
					}

					JSONArray authorsJSONArray = jsonObject.optJSONArray("authors");
					if (authorsJSONArray != null) {
						List<String> authorIds = new ArrayList<>();
						for (int i = 0; i < authorsJSONArray.length(); i++) {
							String authorId = authorsJSONArray.getJSONObject(i).getJSONObject("author").getString("key")
									.replace("/authors/", "");
							authorIds.add(authorId);
						}
						book.setAuthorIds(authorIds);

						List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id))
								.map(optionalAuthor -> {
									if (!optionalAuthor.isPresent())
										return "Unknown Author";
									else
										return optionalAuthor.get().getName();
								}).collect(Collectors.toList());

						book.setAuthorNames(authorNames);
					}

				// Persisit in DB
				System.out.println("Saving book with book name: " + book.getName() + " ...");
				bookRepository.save(book);
					
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			});
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}

}