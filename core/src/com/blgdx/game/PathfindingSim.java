//**********************************************************************************************************************
// Brandon LaPointe - CSC455 - Assignment 2
//	CONTROLS:
//		W,A,S,D : Move Camera
//		Q,E,-,+ : Zoom Out/In
//		TAB : Reset
//	TODO:
//		- FIX: Large map size of hw2input1 is freezing up code at renderGridAndPaths(). Not able to select map squares.
//**********************************************************************************************************************
package com.blgdx.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.*;

public class PathfindingSim extends ApplicationAdapter {
	SpriteBatch batch;
	Texture img;
	int[][] map;
	OrthographicCamera camera;
	float cellWidth = 20; 	// Initial cell width
	float cellHeight = 20; 	// Initial cell height
	int startGridX = -1;
	int startGridY = -1;
	int endGridX = -1;
	int endGridY = -1;
	BitmapFont font;
	boolean[][] visited; // To keep track of visited cells
	int[][] distance; // To store the distance of each cell from the start cell
	int[][] previous; // To store the previous cell in the shortest path
	List<Vector2> shortestPath = new ArrayList<>();
	private Map<Integer, List<Cell>> teleporterConnections = new HashMap<>();	// Map to store teleporter connections

	@Override
	public void create() {
		batch = new SpriteBatch();
		img = new Texture("cell.jpg");
		camera = new OrthographicCamera();
		camera.setToOrtho(false);

		// Read input file
		FileHandle file = Gdx.files.internal("hw2input3.txt");
		String text = file.readString();

		// Split the text into lines
		String[] lines = text.split("\\r?\\n");

		// Remove spaces from each line
		for (int i = 0; i < lines.length; i++) {
			lines[i] = lines[i].replaceAll(" ", "");
		}

		// Initialize the 2D array to hold the values
		map = new int[lines.length][lines[0].length()];

		// Parse each character in the lines and store it in the array
		int rowIdx = 0; // Initialize the row index
		for (int i = 0; i < lines.length; i++) {
			int colIdx = 0; // Initialize the column index for each row
			for (int j = 0; j < lines[i].length(); j++) {
				char c = lines[i].charAt(j);
				if (c == 'T') {
					if (j + 1 < lines[i].length()) { // Check if there's a valid character after 'T'
						char teleporterId = lines[i].charAt(j + 1);
						map[rowIdx][colIdx++] = -Character.getNumericValue(teleporterId); // Store teleporter ID as a negative number
						j++; // Increment j to skip the teleporter ID when processing the next character
					}
				} else if (c == 'F') {
					map[rowIdx][colIdx++] = 10; // Impassable cell
				} else if (Character.isDigit(c)) {
					map[rowIdx][colIdx++] = Character.getNumericValue(c);
				} else {
					map[rowIdx][colIdx++] = 0; // Unknown symbol, mark as 0
				}
			}
			rowIdx++; // Increment the row index after processing each line
		}

		// Initialize visited, distance, and previous arrays
		visited = new boolean[map.length][map[0].length];
		distance = new int[map.length][map[0].length];
		previous = new int[map.length][map[0].length];

		// Initialize teleporter connections
		initializeTeleporterConnections();

		// Initialize visited, distance, and previous arrays
		visited = new boolean[map.length][map[0].length];
		distance = new int[map.length][map[0].length];
		previous = new int[map.length][map[0].length];

		// Initialize font for rendering text
		font = new BitmapFont();
		font.setColor(Color.WHITE);
		font.getData().setScale(1);
	}

	@Override
	public void render() {
		ScreenUtils.clear(0, 0, 0, 1); // Clear the screen with black color
		batch.setProjectionMatrix(camera.combined); // Set the projection matrix

		batch.begin();

		// Render the grid cells and paths
		renderGridAndPaths();

		batch.end(); // End batch rendering here

		// Render text messages
		renderText();

		// Handle user input
		handleInput();
	}

	@Override
	public void dispose() {
		batch.dispose();
		img.dispose();
	}

	private void handleInput() {

		// Adjust cell size based on user input
		if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS) || Gdx.input.isKeyJustPressed(Input.Keys.EQUALS) || Gdx.input.isKeyJustPressed(Input.Keys.E)) {
			cellWidth += 5; // Increase cell width by 5 units
			cellHeight += 5; // Increase cell height by 5 units
		}
		if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) || Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
			cellWidth = Math.max(5, cellWidth - 5); // Decrease cell width by 5 units, but ensure it doesn't go below 5
			cellHeight = Math.max(5, cellHeight - 5); // Decrease cell height by 5 units, but ensure it doesn't go below 5
		}

		// Move camera based on user input
		float cameraSpeed = 7f; // Adjust this value to change camera movement speed
		if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
			camera.translate(0, cameraSpeed);
		}
		if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
			camera.translate(-cameraSpeed, 0);
		}
		if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
			camera.translate(0, -cameraSpeed);
		}
		if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
			camera.translate(cameraSpeed, 0);
		}

		// Update camera
		camera.update();

		// Reset using TAB
		if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
			startGridX = -1;
			startGridY = -1;
			endGridX = -1;
			endGridY = -1;
			visited = new boolean[map.length][map[0].length];
			distance = new int[map.length][map[0].length];
			previous = new int[map.length][map[0].length];
			shortestPath = new ArrayList<>();
		}

		// Update start and end points if clicked
		if (Gdx.input.justTouched()) {
			// Convert mouse coordinates to world coordinates
			Vector3 worldCoordinates = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
			int gridX = (int) (worldCoordinates.x / cellWidth);
			int gridY = (int) ((Gdx.graphics.getHeight() - worldCoordinates.y) / cellHeight); // Adjust for flipped y-coordinate

			// Update start or end points based on mouse button pressed
			if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
				startGridX = gridX;
				startGridY = gridY;
			} else if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
				endGridX = gridX;
				endGridY = gridY;
			}

			calculateShortestPath(); // Recalculate the shortest path when start or end points are updated
		}

		// Close the game if ESC key is pressed
		if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit(); // Close the game
		}
	}

	private void setColorForCell(int i, int j) {
		if (i == startGridY && j == startGridX) {
			batch.setColor(Color.BLUE); // Start cell color
		} else if (i == endGridY && j == endGridX) {
			batch.setColor(Color.BLUE); // End cell color
		} else if (map[i][j] == 10) {
			batch.setColor(Color.BLACK); // Impassable grid cell color
		} else if (map[i][j] == 0) {
			//batch.setColor(Color.RED); // Unknown grid cell color (Also displays a red column on right side of map)
		} else if (map[i][j] < 0) {
			int teleporterId = Math.abs(map[i][j]);
			if (teleporterId == 1) {
				batch.setColor(Color.FIREBRICK); 	// Teleporter 1 color
			} else if (teleporterId == 2) {
				batch.setColor(Color.CHARTREUSE); 	// Teleporter 2 color
			} else if (teleporterId == 3) {
				batch.setColor(Color.TEAL); 		// Teleporter 3 color
			} else {
				batch.setColor(Color.MAROON);		// Teleporters 4, 5, 6, ... color
			}
		} else if (isOnShortestPath(j, i)) {
			batch.setColor(Color.PURPLE); // Most efficient path color
		} else if (visited[i][j] && !isOnShortestPath(j, i)) {
			batch.setColor(Color.GOLDENROD); // Cells visited by any path
		} else {
			float value = 1 - map[i][j] / 9.0f; // Passable grid cell shaded by weight
			Color color = new Color(value, value, value, 1);
			batch.setColor(color);
		}
	}

	private void renderText() {
		batch.begin(); // Begin batch rendering here

		// Display text at the top of the screen
		font.draw(batch, "<CONTROLS> W,A,S,D : Move Camera | Q,E,-,+ : Zoom Out/In | TAB : Reset Screen | ESC : Exit Program", 10, Gdx.graphics.getHeight() + 20);

		batch.end(); // End batch rendering here
	}

	private void renderGridAndPaths() {
		for (int i = 0; i < map.length; i++) {
			for (int j = 0; j < map[i].length; j++) {
				float x = j * cellWidth;
				float y = Gdx.graphics.getHeight() - (i + 1) * cellHeight;

				// Set cell color based on type
				setColorForCell(i, j);

				batch.draw(img, x, y, cellWidth, cellHeight);
				batch.setColor(Color.WHITE); // Reset batch color to default
			}
		}
		// Render the shortest path
		renderShortestPath();
	}

	private void renderShortestPath() {
		// Render the shortest path if available
		if (!shortestPath.isEmpty()) {
			for (Vector2 point : shortestPath) {
				float x = point.x * cellWidth;
				float y = Gdx.graphics.getHeight() - (point.y + 1) * cellHeight;
			}
		}
	}

	private void dijkstra(int startX, int startY) {
		// Initialize distance array
		for (int i = 0; i < map.length; i++) {
			for (int j = 0; j < map[i].length; j++) {
				distance[i][j] = Integer.MAX_VALUE;
			}
		}
		distance[startY][startX] = 0;

		// Initialize previous array
		for (int i = 0; i < map.length; i++) {
			for (int j = 0; j < map[i].length; j++) {
				previous[i][j] = -1; // No previous cell initially
			}
		}

		// Priority queue to store cells to be visited
		PriorityQueue<Cell> pq = new PriorityQueue<>(Comparator.comparingInt(c -> distance[c.y][c.x]));
		pq.add(new Cell(startX, startY));

		batch.begin(); // Begin batch rendering

		while (!pq.isEmpty()) {
			Cell current = pq.poll();
			int x = current.x;
			int y = current.y;

			// Mark current cell as visited
			visited[y][x] = true;

			// Set color of the current cell to red
			batch.setColor(Color.RED);
			float cellX = x * cellWidth;
			float cellY = Gdx.graphics.getHeight() - (y + 1) * cellHeight;
			batch.draw(img, cellX, cellY, cellWidth, cellHeight);

			// Handle teleporters
			if (map[y][x] < 0) { // If the current cell is a teleporter
				int teleporterId = map[y][x];
				List<Cell> connections = teleporterConnections.get(teleporterId);
				if (connections != null) {
					for (Cell connection : connections) {
						int newX = connection.x;
						int newY = connection.y;
						// Ensure the connection is not at the same coordinates as the teleporter
						if (newX != x || newY != y) {
							// Check if the neighboring cell is passable
							if (isValidCell(newX, newY)) {
								int newDistance = distance[y][x] + getCellWeight(newX, newY);
								if (newDistance < distance[newY][newX]) {
									distance[newY][newX] = newDistance;
									previous[newY][newX] = y * map[0].length + x; // Store the previous cell
									pq.add(new Cell(newX, newY));
								}
							}
						}
					}
				}
			} else { // If the current cell is not a teleporter, visit neighbors
				for (int i = -1; i <= 1; i++) {
					for (int j = -1; j <= 1; j++) {
						if (i == 0 && j == 0) continue; // Skip current cell
						int newX = x + j;
						int newY = y + i;
						if (isValidCell(newX, newY)) {
							int newDistance = distance[y][x] + getCellWeight(newX, newY);
							if (newDistance < distance[newY][newX]) {
								distance[newY][newX] = newDistance;
								previous[newY][newX] = y * map[0].length + x; // Store the previous cell
								pq.add(new Cell(newX, newY));
							}
						}
					}
				}
			}
		}

		batch.end(); // End batch rendering
	}

	private boolean isValidCell(int x, int y) {
		return x >= 0 && x < map[0].length && y >= 0 && y < map.length && map[y][x] != 10 && !visited[y][x];
	}

	private int getCellWeight(int x, int y) {
		if (map[y][x] < 0) {
			// Teleporter cell with zero weight
			return 0;
		} else {
			// Normal cell, return value of cell
			return map[y][x];
		}
	}

	private void initializeTeleporterConnections() {
		// Clear existing connections
		teleporterConnections.clear();

		// Store teleporter coordinates
		Map<Integer, List<Cell>> teleporterCoordinates = new HashMap<>();

		// Iterate through the map to find teleporter cells and store their coordinates
		for (int i = 0; i < map.length; i++) {
			for (int j = 0; j < map[i].length; j++) {
				if (map[i][j] < 0) { // Check if the cell is a teleporter cell
					int teleporterId = map[i][j]; // Get the teleporter ID

					// If teleporter ID is not already present in the map, create a new entry
					if (!teleporterCoordinates.containsKey(teleporterId)) {
						teleporterCoordinates.put(teleporterId, new ArrayList<>());
					}

					// Add the cell coordinates to the list of teleporter coordinates
					teleporterCoordinates.get(teleporterId).add(new Cell(j, i));
				}
			}
		}

		// Iterate through each teleporter type and its coordinates
		for (int id : teleporterCoordinates.keySet()) {
			List<Cell> teleporterCells = teleporterCoordinates.get(id);

			// If teleporter ID is not already present in the connections map, create a new entry
			if (!teleporterConnections.containsKey(id)) {
				teleporterConnections.put(id, new ArrayList<>());
			}

			// Iterate through each teleporter cell of the current type
			for (Cell cell : teleporterCells) {
				int x = cell.x;
				int y = cell.y;

				// Iterate over neighboring grid cells
				for (int yOffset = -1; yOffset <= 1; yOffset++) {
					for (int xOffset = -1; xOffset <= 1; xOffset++) {
						int neighborX = x + xOffset;
						int neighborY = y + yOffset;

						// Skip the center cell and out-of-bounds cells
						if ((xOffset != 0 || yOffset != 0) && neighborX >= 0 && neighborX < map[0].length
								&& neighborY >= 0 && neighborY < map.length) {

							// Check if the neighbor is passable and not a teleporter
							if (map[neighborY][neighborX] != 10 && map[neighborY][neighborX] >= 0) {

								// Add the neighbor's coordinates to the list of connections for the current teleporter type
								teleporterConnections.get(id).add(new Cell(neighborX, neighborY));
							}
						}
					}
				}
			}
		}
	}

	private List<Vector2> dijkstraPathfinding(int startX, int startY, int endX, int endY) {
		// Perform Dijkstra's algorithm to populate the distance and previous arrays
		dijkstra(startX, startY);

		// Retrieve the shortest path from the previous array
		List<Cell> pathCells = getShortestPath(endX, endY);

		// Convert cell coordinates to Vector2 for rendering
		List<Vector2> path = new ArrayList<>();
		for (Cell cell : pathCells) {
			path.add(new Vector2(cell.x, cell.y));
		}
		return path;
	}

	private List<Cell> getShortestPath(int endX, int endY) {
		List<Cell> path = new ArrayList<>();
		int x = endX;
		int y = endY;
		while (previous[y][x] != -1) {
			path.add(new Cell(x, y));
			int prevIndex = previous[y][x];
			x = prevIndex % map[0].length;
			y = prevIndex / map[0].length;
		}
		Collections.reverse(path);
		return path;
	}

	private boolean isOnShortestPath(int x, int y) {
		for (Vector2 point : shortestPath) {
			if ((int) point.x == x && (int) point.y == y) {
				return true;
			}
		}
		return false;
	}

	private void calculateShortestPath() {
		if (startGridX != -1 && startGridY != -1 && endGridX != -1 && endGridY != -1) {
			shortestPath = dijkstraPathfinding(startGridX, startGridY, endGridX, endGridY);
		}
	}

	private class Cell {
		int x;	// X-position within the map
		int y;	// Y-position within the map
		ArrayList<Cell> neighbors;	// List of neighboring cells

		Cell(int x, int y) {
			this.x = x;
			this.y = y;
			this.neighbors = new ArrayList<>();
		}
	}
}
