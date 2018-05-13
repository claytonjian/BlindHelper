package application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import utilities.Utilities;

public class Controller {

	@FXML
	private ImageView imageView; // the image display window in the GUI
	@FXML
	private Slider slider;
	@FXML
	private VBox vBox;
	@FXML
	private Slider volume;
	@FXML
	private Label title;

	private Mat image;
	private VideoCapture capture;
	private VideoCapture soundCapture;
	private ScheduledExecutorService timer;
	private ScheduledExecutorService soundTimer;

	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	private double currentFrameNumber;
	private double framePerSecond;
	private double totalFrameCount;
	private SourceDataLine sourceDataLine;
	private int bufferSize = 4096;
	private boolean finishedChoosing;
	private boolean textValid;

	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values


		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;

		numberOfQuantizionLevels = 16;

		numberOfSamplesPerColumn = 500;
		
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}

		// assign frequencies for each particular row
		title.setText("Blind Helper");
		volume.valueProperty().addListener(new ChangeListener<Number>() {
			public void changed(ObservableValue<? extends Number> ov,
					Number oldValue, Number newValue) {
				volume.setValue(newValue.doubleValue());
			}
		});
	}

	private String getImageFilename(File video) {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
		return video.getAbsolutePath();
	}

	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		Stage openStage = new Stage();
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select file to open");
		File file = null;

		// Open dialog screen to open files
		try{
			file = fileChooser.showOpenDialog(openStage);
		}
		catch (Exception e){
			System.out.println("Open Dialog cannot be shown");
		}
		if (file == null){
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning");
			alert.setHeaderText("No file selected");
			alert.setContentText("Please choose a file before continuing!");
			alert.showAndWait();
		}
		else{
			// This method opens an image and display it using the GUI
			// You should modify the logic so that it opens and displays a video
			capture = new VideoCapture(getImageFilename(file)); // open video file
			soundCapture = new VideoCapture(getImageFilename(file));
			title.setText(file.getName());
			if (capture.isOpened()) { // open successfully
				createFrameGrabber(0,0);
			}
			else{
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle("Error");
				alert.setHeaderText("File is not a video");
				alert.setContentText("Please select a video file to proceed!");

				alert.showAndWait();
			}
		}
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
	}

	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		// convert the image from RGB to grayscale
		Runnable playback = new Runnable() {
			@Override
			public void run() {
				File audioFile = new File("resources/click.wav");
				image = new Mat();
				while(soundCapture.read(image)){
					try{
						Mat grayImage = new Mat();
						Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
						// resize the image
						Mat resizedImage = new Mat();
						Imgproc.resize(grayImage, resizedImage, new Size(width, height));

						// quantization
						double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
						for (int row = 0; row < resizedImage.rows(); row++) {
							for (int col = 0; col < resizedImage.cols(); col++) {
								roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
							}
						}

						// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
						AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
						sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
						sourceDataLine.open(audioFormat, sampleRate);
						sourceDataLine.start();
						setVolume(sourceDataLine, volume.getValue());
						for (int col = 0; col < width; col++) {
							byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
							for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
								double signal = 0;
								for (int row = 0; row < height; row++) {
									int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
									int time = t + col * numberOfSamplesPerColumn;
									double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
									signal += roundedImage[row][col] * ss;
								}
								double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
								audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
							}
							sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
						}
						sourceDataLine.drain();
						sourceDataLine.close();

						// Play click sound
						AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
						audioFormat = audioStream.getFormat();
						DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
						sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
						sourceDataLine.open(audioFormat);
						sourceDataLine.start();
						setVolume(sourceDataLine, volume.getValue());
						byte[] bytesBuffer = new byte[bufferSize];
						int bytesRead = -1;
						while((bytesRead = audioStream.read(bytesBuffer)) != -1){
							sourceDataLine.write(bytesBuffer, 0, bytesRead);
						}
						sourceDataLine.drain();
						sourceDataLine.close();
						audioStream.close();
					} catch (Exception e) {
						System.out.println("Audio cannot play");
					}
				}
			}
		};
		// terminate the timer if it is running
		if (soundTimer != null && !soundTimer.isShutdown()) {
			soundTimer.shutdown();
			try{
				soundTimer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			} catch(Exception e){
				System.out.println("Can't stop sound");
			}
		}
		// run the playback
		soundTimer = Executors.newSingleThreadScheduledExecutor();
		soundTimer.schedule(playback, 0, TimeUnit.MILLISECONDS);
	}
	protected void createFrameGrabber(double curFrameNumber, double totFrameCount) throws InterruptedException {
		if (capture != null && capture.isOpened()) { // the video must be open
			framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
			slider.setValue(0);
			// create a runnable to fetch new frames periodically
			Runnable frameGrabber = new Runnable() {
				@Override
				public void run() {
					Mat frame = new Mat();
					if (capture.read(frame)) { // decode successfully
						Image im = Utilities.mat2Image(frame);
						Utilities.onFXThread(imageView.imageProperty(), im);
						currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
						slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
					}
					else { // reach the end of the video
						capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);	// start from desired frame
					}
				}
			};
			// terminate the timer if it is running
			if (timer != null && !timer.isShutdown()) {
				timer.shutdown();
				timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			}
			// run the frame grabber
			timer = Executors.newSingleThreadScheduledExecutor();
			timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
		}
	}
	@FXML
	protected void changeSettings(ActionEvent event) throws Exception{
		List<String> choices = new ArrayList<>();
		choices.add("Width");
		choices.add("Height");
		choices.add("Sample rate");
		choices.add("Sample size in bits");
		choices.add("Number of channels");
		choices.add("Number of quantizion levels");
		choices.add("Number of samples per column");

		ChoiceDialog<String> choiceDialog = new ChoiceDialog<>("", choices);
		choiceDialog.setTitle("Change Settings");
		choiceDialog.setHeaderText("Select a setting you would like to change");
		choiceDialog.setContentText("Choose your setting:");
		finishedChoosing = false;
		while(!finishedChoosing){
			Optional<String> choiceResult = choiceDialog.showAndWait();
			if(choiceResult.isPresent()){
				TextInputDialog textDialog;
				Optional<String> textResult;
				switch (choiceResult.toString().substring(9, choiceResult.toString().length() - 1)) {
				case "Width":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + width);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								width = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				case "Height":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + height);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								height = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
								assignFrequency();
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				case "Sample rate":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + sampleRate);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								sampleRate = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				case "Sample size in bits":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + sampleSizeInBits);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								sampleSizeInBits = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				case "Number of channels":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + numberOfChannels);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								numberOfChannels = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				case "Number of quantizion levels":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + numberOfQuantizionLevels);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								numberOfQuantizionLevels = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				case "Number of samples per column":
					textValid = false;
					while(!textValid){
						textDialog = new TextInputDialog("" + numberOfSamplesPerColumn);
						textDialog.setTitle("Change Settings");
						textDialog.setHeaderText("Pick a new value for " + choiceResult.toString().substring(9, choiceResult.toString().length() - 1).toLowerCase());
						textDialog.setContentText("New value:");

						textResult = textDialog.showAndWait();
						if (textResult.isPresent()){
							try{
								numberOfSamplesPerColumn = Integer.parseInt(textResult.toString().substring(9, textResult.toString().length() - 1));
								textValid = true;
							} catch(Exception e){
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error");
								alert.setHeaderText("Value is not an integer");
								alert.setContentText("Please enter an integer to proceed!");
								alert.showAndWait();
							}
						}
					}
					break;
				}
			}
			else{
				finishedChoosing = true;
			}
		}
	}
	private void setVolume(SourceDataLine source, double volumeValue){
		try {
			FloatControl gainControl=(FloatControl)source.getControl(FloatControl.Type.MASTER_GAIN);
			BooleanControl muteControl=(BooleanControl)source.getControl(BooleanControl.Type.MUTE);
			if (volumeValue == 0) {
				muteControl.setValue(true);
			}
			else {
				muteControl.setValue(false);
				gainControl.setValue((float)(Math.log(volumeValue / 100d) / Math.log(10.0) * 20.0));
			}
		}
		catch (Exception e) {
			System.out.println("Cannot change volume");
		}
	}
	private void assignFrequency(){
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
	}
}
